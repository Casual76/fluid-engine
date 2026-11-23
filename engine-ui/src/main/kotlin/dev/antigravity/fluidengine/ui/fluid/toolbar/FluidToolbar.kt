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
import dev.antigravity.fluidengine.ui.fluid.FluidToolbarSplitKind
import dev.antigravity.fluidengine.ui.fluid.FluidToolbarSplitType
import dev.antigravity.fluidengine.ui.fluid.FluidUndoCountdown
import dev.antigravity.fluidengine.ui.fluid.fluidSplitTargets
import dev.antigravity.fluidengine.ui.fluid.fluidTargetAt
import dev.antigravity.fluidengine.ui.fluid.TrackFluidUndoDeadline
import dev.antigravity.fluidengine.ui.fluid.fluidUndoDescription
import dev.antigravity.fluidengine.ui.fluid.fluidUndoFill
import dev.antigravity.fluidengine.ui.fluid.rememberFluidUndoDescription
import dev.antigravity.fluidengine.ui.fluid.rememberFluidUndoFill
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
import dev.antigravity.fluidengine.ui.fluid.fluidGlassSoftMaterialize
import dev.antigravity.fluidengine.ui.fluid.currentFluidGlassContainer
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.State
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.snapshots.Snapshot
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ClipOp
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.layer.GraphicsLayer
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.layout
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.SemanticsPropertyReceiver
import androidx.compose.ui.semantics.Role
import dev.antigravity.fluidengine.ui.fluid.action.FluidActionPreset
import dev.antigravity.fluidengine.ui.fluid.action.FluidActionState
import dev.antigravity.fluidengine.ui.fluid.action.fluidToolbarActionType
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
  internal var keyboard: FluidKeyboardDeclaration? = null
  internal var titleMode: FluidToolbarTitleMode = FluidToolbarTitleMode.Large
  internal var subtitle: String? = null
  internal var search: FluidSearchSpec? = null
  internal var overflowLabel: String? = null
  /** Il titolo che diventa selettore (F6B.11), dichiarato da [title]. */
  internal var titleSelector: FluidTitleSelector? = null
  private var inOverflow = false
  private var groupId: Any? = null
  private var spacers = 0

  /**
   * Come si mostra il titolo ([FluidToolbarTitleMode]) e, se c'è, il sottotitolo: sotto il titolo grande finché non si
   * aggancia, e poi sotto il titolo agganciato, più piccolo.
   */
  fun title(
    mode: FluidToolbarTitleMode = FluidToolbarTitleMode.Large,
    subtitle: String? = null,
    /**
     * **Il titolo che diventa selettore** (F6B.11; `toolbarTitleMenu { }` di SwiftUI): il titolo, in linea e grande, porta
     * accanto il piccolo disco con la freccia di iOS 26 ed è la sorgente di questo menu (gli account, le cartelle), che
     * nasce dal titolo e ci rientra (F5A.2). Al posto di [panel].
     */
    menu: (() -> List<FluidContextAction>)? = null,
    /**
     * Il titolo come sorgente di un **pannello** con un contenuto dell'app (le località, con la ricerca e la lista): un
     * popover dal titolo (F5B.1), che sotto i 600 dp si adatta a foglio dal titolo. [FluidTitlePanelScope.dismiss] lo
     * chiude. Al posto di [menu].
     */
    panel: (@Composable FluidTitlePanelScope.() -> Unit)? = null,
    /** Che cosa fa il titolo, per TalkBack («Cambia account»), quando è un selettore; null: il nome del titolo e basta. */
    selectorHint: String? = null,
  ) {
    titleMode = mode
    if (subtitle != null) this.subtitle = subtitle
    require(menu == null || panel == null) { "il titolo apre un menu o un pannello, non tutti e due" }
    titleSelector = if (menu == null && panel == null) null else FluidTitleSelector(menu, panel, selectorHint)
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
    /** Un tasto con esito (F6B.3a): il preset, per esempio [FluidActionPreset.Save]. Vedi [button]. */
    action: FluidActionPreset? = null,
    /** Lo stato del tasto con esito, dell'app o di un [dev.antigravity.fluidengine.ui.fluid.action.FluidActionRunner]. */
    state: FluidActionState = FluidActionState.Idle,
  ) {
    entries += FluidToolbarButtonEntry(
      id = id, role = FluidToolbarRole.Confirm, placement = FluidToolbarPlacement.ConfirmationAction,
      icon = FluidIcons.Check, label = label, onClick = onClick, enabled = enabled, tint = tint,
      defaultLabel = FluidToolbarDefaultLabel.Confirm, overflow = inOverflow, type = fluidToolbarActionType(action, state),
    )
  }

  /**
   * L'azione principale con un'icona sua: vetro tinto (di serie l'accento), icona chiara, da sola a destra. Una sola per
   * barra, come la conferma, che è un'azione principale anche lei.
   *
   * Con [action] e [state] è un tasto con esito, come [button].
   */
  fun primary(
    id: Any,
    icon: ImageVector,
    label: String,
    onClick: () -> Unit,
    enabled: Boolean = true,
    tint: Color = Color.Unspecified,
    action: FluidActionPreset? = null,
    state: FluidActionState = FluidActionState.Idle,
    /** Finestra di annullamento temporanea, conservando ID, posto e azione a riposo. */
    undo: FluidUndoCountdown? = null,
  ) {
    val pending = undo?.takeIf { it.state.isActive }
    entries += FluidToolbarButtonEntry(
      id = id, role = FluidToolbarRole.Primary, placement = FluidToolbarPlacement.PrimaryAction,
      icon = icon.takeIf { pending == null }, text = pending?.label,
      label = pending?.accessibilityLabel ?: label, onClick = pending?.tap() ?: onClick,
      enabled = enabled, tint = tint, overflow = inOverflow,
      type = if (pending == null) fluidToolbarActionType(action, state) else null, undo = pending,
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
   * @param action un **tasto con esito** (F6B.3a): il preset ([FluidActionPreset.Send], `Save`, `Copy`, `Download`,
   *   `Upload`, `Favorite` o uno su misura) dà le facce di ogni stato e le parole per TalkBack. [icon] resta l'identità del
   *   tasto nella barra (e la sua icona nel menu «Altro»); dentro il vetro si vedono le facce del preset. Il vetro e la
   *   sua misura non cambiano con lo stato: cambia solo il contenuto, sfocato (R7).
   * @param state lo stato del tasto con esito: dell'app, o di un
   *   [dev.antigravity.fluidengine.ui.fluid.action.FluidActionRunner]. Mentre lavora il tasto non risponde.
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
    action: FluidActionPreset? = null,
    state: FluidActionState = FluidActionState.Idle,
    /** Finestra di annullamento temporanea, conservando ID, posto e azione a riposo. */
    undo: FluidUndoCountdown? = null,
  ) {
    val pending = undo?.takeIf { it.state.isActive }
    entries += FluidToolbarButtonEntry(
      id = id, role = role.toolbarRole(FluidToolbarRole.Action), placement = role.placementFor(placement),
      priority = priority, icon = icon.takeIf { pending == null }, text = pending?.label,
      label = pending?.accessibilityLabel ?: label, onClick = pending?.tap() ?: onClick, tint = tint,
      selected = selected && pending == null,
      enabled = enabled, badgeCount = badge.takeIf { pending == null }, badgeDot = badgeDot && pending == null,
      sharedBackground = sharedBackground, destructive = role == FluidButtonRole.Destructive, overflow = inOverflow,
      type = if (pending == null) fluidToolbarActionType(action, state) else null, undo = pending,
    )
  }

  /** Un contatore con un vetro proprio. Alla fine esce con la lente del piano. */
  fun undoCountdown(
    id: Any,
    countdown: FluidUndoCountdown,
    placement: FluidToolbarPlacement = FluidToolbarPlacement.Automatic,
    priority: FluidToolbarPriority = FluidToolbarPriority.High,
  ) {
    if (!countdown.state.isActive) return
    entries += FluidToolbarButtonEntry(
      id = id, role = FluidToolbarRole.Text, placement = placement, priority = priority,
      text = countdown.label, label = countdown.accessibilityLabel, onClick = countdown.tap(),
      overflow = inOverflow, undo = countdown, transientUndo = true,
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

  /**
   * Un'azione con **un'azione sua e un menu nascosto** (R1 N5, F6B.11; `Menu(content:label:primaryAction:)` di SwiftUI):
   * il tocco fa [onClick], la pressione lunga apre il menu dallo stesso vetro (in un gruppo con lo sfondo condiviso, dal
   * vetro del gruppo), col dito che può scegliere una voce senza staccarsi. TalkBack trova il menu fra le azioni del tasto.
   * Nel menu «Altro» l'azione viene prima, poi le voci del menu. Per una freccia visibile accanto all'azione: [split].
   */
  fun action(
    id: Any,
    icon: ImageVector,
    label: String,
    onClick: () -> Unit,
    placement: FluidToolbarPlacement = FluidToolbarPlacement.Automatic,
    priority: FluidToolbarPriority = FluidToolbarPriority.High,
    enabled: Boolean = true,
    menu: () -> List<FluidContextAction>,
  ) {
    entries += FluidToolbarButtonEntry(
      id = id, role = FluidToolbarRole.Action, placement = placement, priority = priority,
      icon = icon, label = label, onClick = onClick, enabled = enabled, overflow = inOverflow, menu = menu,
    )
  }

  /**
   * **Il tasto diviso** (F6B.11, idea 9 di R8; scelta di Alessio del 21 novembre 2026: «un vetro solo, con un filo sottile fra
   * azione e freccia»): un vetro solo con due zone, la scritta [text] che fa [onClick] e la freccia che apre [menu] **dallo
   * stesso vetro** (F5A.2). Anche la pressione lunga sulla scritta apre il menu (R1 N5). È un comando solo, quindi un gruppo a
   * sé: non si unisce alle icone vicine. «Invia ▾» con «Programma invio».
   *
   * I bersagli sono due e disgiunti, ognuno di almeno 48 × 48 dp anche dove la zona disegnata è più stretta (la freccia):
   * TalkBack li legge come due pulsanti, [label] (di serie la scritta) e [menuLabel] (di serie «Altro» con il nome
   * dell'azione). Nel menu «Altro» l'azione viene prima, poi le voci del menu.
   */
  fun split(
    id: Any,
    text: String,
    onClick: () -> Unit,
    label: String = text,
    menuLabel: String? = null,
    placement: FluidToolbarPlacement = FluidToolbarPlacement.Automatic,
    priority: FluidToolbarPriority = FluidToolbarPriority.High,
    enabled: Boolean = true,
    menu: () -> List<FluidContextAction>,
  ) {
    entries += FluidToolbarButtonEntry(
      id = id, role = FluidToolbarRole.Text, placement = placement, priority = priority, text = text, label = label,
      onClick = onClick, enabled = enabled, overflow = inOverflow, menu = menu,
      type = FluidToolbarSplitType(FluidToolbarSplitKind(text, iconWidthDp = null), menuLabel),
    )
  }

  /** Il tasto diviso con un'icona al posto della scritta ([label] è il suo nome per TalkBack): vedi l'altra [split]. */
  fun split(
    id: Any,
    icon: ImageVector,
    label: String,
    onClick: () -> Unit,
    menuLabel: String? = null,
    placement: FluidToolbarPlacement = FluidToolbarPlacement.Automatic,
    priority: FluidToolbarPriority = FluidToolbarPriority.High,
    enabled: Boolean = true,
    menu: () -> List<FluidContextAction>,
  ) {
    entries += FluidToolbarButtonEntry(
      id = id, role = FluidToolbarRole.Text, placement = placement, priority = priority, icon = icon, label = label,
      onClick = onClick, enabled = enabled, overflow = inOverflow, menu = menu,
      type = FluidToolbarSplitType(FluidToolbarSplitKind(icon, iconWidthDp = FluidMetrics.GlassButtonLens.value), menuLabel),
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

  /**
   * Dichiara i comandi della tastiera per il [context] corrente. Un id comune resta ancorato quando il contesto cambia.
   * [fieldFocused] può seguire il focus del campo con `onFocusChanged`; la schermata rileva il focus al suo interno
   * quando è null. [alwaysVisible] tiene i comandi in vista anche quando si chiude la tastiera.
   */
  fun keyboard(
    context: Any,
    alwaysVisible: Boolean = false,
    fieldFocused: Boolean? = null,
    content: FluidToolbarScope.() -> Unit,
  ) {
    keyboard = FluidKeyboardDeclaration(context, alwaysVisible, fieldFocused, buildFluidToolbar(null, content))
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

private fun FluidUndoCountdown.tap(): () -> Unit = { undo() }

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
  /** Il tipo registrato (F6B.3a): vedi [FluidToolbarElementType]. Null: un tasto di sempre. */
  val type: FluidToolbarElementType? = null,
  val undo: FluidUndoCountdown? = null,
  val transientUndo: Boolean = false,
) : FluidToolbarEntry

/**
 * **Il punto unico di registrazione degli elementi nuovi della barra** (F6B.3a), lato resa: ciò che un tipo d'elemento
 * nuovo dice alla barra oltre al modello ([kind], [FluidToolbarKind]). La barra lo chiede in un posto solo per ciascuna
 * cosa, e non sa niente del tipo:
 *
 * - [kind]: il lato puro, che il modello usa per le chiavi del contenuto e del vetro e per la larghezza;
 * - [acceptsTap]: se il posto risponde adesso al tocco (un tasto con esito che lavora no);
 * - [rememberSlot]: la resa del tipo in un posto (la faccia, dentro lo scambio sfocato del posto) e le sue parole per
 *   TalkBack, ricordata una volta per posto.
 *
 * Il tasto con esito ne è il primo (`FluidToolbarActionType`, in `action/FluidActionButton.kt`). Per aggiungerne uno
 * (F6B.9, F6B.10, F6B.11): il suo [FluidToolbarKind] con una riga in [FluidToolbarKinds], questa classe estesa nel file del
 * tipo, e una funzione della DSL che aggiunge un [FluidToolbarButtonEntry] con `type =` il tipo, scegliendo un ruolo che
 * c'è già (`Action` per un'icona che può stare in gruppo, `Text` per un elemento col suo vetro). La risoluzione, la
 * differenza fra due stati e il piano non cambiano.
 */
internal abstract class FluidToolbarElementType {
  /** Il lato puro del tipo, confrontabile per valore. */
  abstract val kind: FluidToolbarKind

  /** Se il posto risponde al tocco in questo stato. */
  open val acceptsTap: Boolean get() = true

  /**
   * Il tipo come lo ha posato la barra, dato il [kind] dell'elemento risolto: lui stesso, o la sua forma più stretta se la
   * barra ha scelto [FluidToolbarKind.narrower] (F6B.10, la pillola minima). La barra lo chiede in un punto solo, prima di
   * [rememberSlot].
   */
  open fun resolvedAs(kind: FluidToolbarKind?): FluidToolbarElementType = this

  /**
   * La resa del tipo in un posto. [live] è vero per il posto vero (suona l'aptica, trema), falso per una copia (il glifo
   * che viaggia fra due vetri), che disegna soltanto.
   */
  @Composable
  abstract fun rememberSlot(live: Boolean): FluidToolbarTypeSlot

  /**
   * La zona di fine del posto che apre il menu, se il tipo ne ha una (F6B.11, la freccia del tasto diviso): il posto ha
   * allora **due bersagli**, l'azione e la freccia, disgiunti e ognuno di almeno 48 dp. Null: un bersaglio solo.
   */
  open val menuZone: Dp? get() = null

  /** Il nome per TalkBack del bersaglio di [menuZone], o null per quello di serie («Altro» con il nome del tasto). */
  open val menuZoneLabel: String? get() = null
}

/** La resa di un [FluidToolbarElementType] in un posto: la faccia e le parole. */
internal interface FluidToolbarTypeSlot {
  /**
   * La faccia, centrata nel posto, con l'inchiostro [color] (già risolto: chiaro sul vetro tinto della principale) e la
   * taglia [iconSize]. [onTint] è vero sul vetro tinto.
   */
  @Composable
  fun Face(color: Color, iconSize: Dp, onTint: Boolean)

  /** Le parole per TalkBack sul bersaglio del posto. [name] è il nome che il tasto ha a riposo. */
  fun describe(receiver: SemanticsPropertyReceiver, name: String?)
}

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
  val keyboard: FluidKeyboardDeclaration? = null,
  /** Il titolo che diventa selettore (F6B.11), o null: il titolo di sempre. */
  val titleSelector: FluidTitleSelector? = null,
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
  val declared = FluidToolbarDeclaration(scope.entries.toList(), scope.titleMode, scope.subtitle, scope.search, scope.overflowLabel,
    scope.keyboard, scope.titleSelector)
  val exits = declared.buttons.values.any { it.role == FluidToolbarRole.Back || it.role == FluidToolbarRole.Close }
  if (onBack == null || exits) return declared
  val back = FluidToolbarButtonEntry(
    id = FluidToolbarIds.Back, role = FluidToolbarRole.Back, placement = FluidToolbarPlacement.Navigation,
    icon = FluidIcons.ArrowBackIosNew, onClick = onBack, defaultLabel = FluidToolbarDefaultLabel.Back,
  )
  return FluidToolbarDeclaration(listOf(back) + declared.entries, scope.titleMode, scope.subtitle, scope.search, scope.overflowLabel,
    scope.keyboard, scope.titleSelector)
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
  keyboardContext: Any? = null,
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
      widthDp = entry.type?.kind?.widthDp ?: when {
        entry.custom != null -> entry.width?.value
        // F6B.11: il tasto diviso aggiunge alla scritta il filo e la zona della freccia ([FluidToolbarKind.trailingDp]).
        entry.text != null -> textWidthDp(entry.text) + (entry.type?.kind?.trailingDp ?: 0f)
        else -> null
      },
      // Un elemento libero cambia contenuto da sé: la sua identità è anche la chiave del contenuto.
      contentKey = if (entry.custom != null) FluidToolbarCustomContent(entry.id) else null,
      kind = entry.type?.kind,
      transition = if (entry.transientUndo) FluidToolbarTransition.Materialize else FluidToolbarTransition.Automatic,
      keyboardContext = keyboardContext,
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
 * Un gruppo che arriva **nello stesso posto** di uno che se ne va (F6B.11, [FluidToolbarGroupDiff.inPlace]) ne eredita il
 * vetro: un tasto che cambia resta un vetro solo. Chi non eredita ha un vetro nuovo, mai uguale a uno di prima. Pura.
 */
internal fun fluidToolbarGlassIds(
  resolved: FluidToolbarResolved,
  previous: Map<FluidToolbarGroupId, FluidToolbarGlassId> = emptyMap(),
  before: FluidToolbarResolved? = null,
  diff: FluidToolbarDiff? = null,
  keepUnmatchedGroupsBySide: Boolean = false,
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
    // F6B.11: un vetro che cambia nello stesso posto è lo stesso vetro (l'indietro che diventa «Seleziona tutto»): il piano
    // lo vede con lo stesso id, quindi lo tiene e ne cambia la misura con la molla, e il contenuto si scambia sfocato.
    for (group in diff.groups) {
      if (!group.inPlace) continue
      val from = group.from.single().id
      val to = group.to.single().id
      if (from in taken || to in result) continue
      val inherited = previous[from] ?: continue
      result[to] = inherited
      taken += from
    }
    // Nella tastiera lo stesso posto passa fra contesti anche se cambiano tutti i comandi.
    if (keepUnmatchedGroupsBySide) for (group in resolved.groups) {
      if (group.id in result) continue
      val source = before.groups
        .filter { it.side == group.side && it.id !in taken && it.id in previous }
        .minByOrNull { kotlin.math.abs(it.leftDp - group.leftDp) } ?: continue
      result[group.id] = previous.getValue(source.id)
      taken += source.id
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

  /**
   * [ridesEdge]: il tasto che resta al suo posto in un vetro che cambia larghezza va col bordo del vetro a cui è ancorato
   * ([FluidToolbarEdgeRide]). Lo chiede solo la barra in basso (scelta di Alessio del 19 novembre 2026, il «+» di Foto);
   * la barra in alto lo tiene fermo come ha scelto F4A.
   */
  fun update(next: FluidToolbarResolved, motion: FluidToolbarMotion, searchActive: Boolean? = null, ridesEdge: Boolean = false) {
    val before = resolved
    val searchChanged = searchActive != null && previousSearchActive != null && searchActive != previousSearchActive
    previousSearchActive = searchActive
    if (before == next) return
    if (before == null) {
      glassIds = fluidToolbarGlassIds(next)
      previousIds = emptySet()
    } else {
      val diff = diffFluidToolbar(before, next, FluidToolbarTrigger.State, motion)
      val oldContext = before.elements.firstNotNullOfOrNull { it.item.keyboardContext }
      val newContext = next.elements.firstNotNullOfOrNull { it.item.keyboardContext }
      val changesKeyboardContext = oldContext != null && newContext != null && oldContext != newContext
      val ids = fluidToolbarGlassIds(next, glassIds, before, diff, keepUnmatchedGroupsBySide = changesKeyboardContext)
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
        val ride = if (ridesEdge && !cross && resized) fluidToolbarEdgeRide(sourceGroup, targetGroup) else null
        val trip = FluidToolbarTransfer(if (cross && leavesSingle) from else to,
          cross && leavesSingle, cross && entersSingle, start.centerDp, end.centerDp, transfers[end.item.id]?.lastCenter,
          !cross && resized, targetGroup.widthDp, start.item, ride)
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

/**
 * Il bordo del vetro a cui è ancorato un tasto che resta al suo posto mentre il suo vetro cambia larghezza: il gruppo
 * d'arrivo ([leftDp], [widthDp], come lo posa la barra) e il lato ([end]: il bordo di fine, altrimenti quello d'inizio).
 *
 * Perché: nelle clip a tempo vero di Apple (WWDC25 219, Foto, f8551; la trascrizione non dice di rallentamenti) il «+»
 * che resta mentre la capsula di tre si chiude su di lui **va col suo vetro**: il vetro è trascinato dal bordo che si
 * muove ([dev.antigravity.fluidengine.ui.fluid.FluidMotion.PlaneResizeDrag]) e il «+» sta al centro del lobo di destra,
 * +0,16 H a 300 ms, di nuovo al posto a ~490, −0,016 H a ~560 (`sessioni/scelte-b/rapporto.md`). È la regola del
 * contenuto che resta nel gruppo ([dev.antigravity.fluidengine.ui.fluid.FluidGlassContentAnchor]): «il rimbalzo del
 * bordo resta nel contenuto».
 */
internal data class FluidToolbarEdgeRide(val leftDp: Float, val widthDp: Float, val end: Boolean) {
  /**
   * Quanto il bordo d'ancoraggio del vetro in volo ([flightLeft]..[flightRight], px nella barra) è lontano dal suo posto
   * di riposo, con gli stessi arrotondamenti con cui la barra posa il gruppo (`offset` e `size` in px interi). È lo
   * spostamento che [dev.antigravity.fluidengine.ui.fluid.fluidGlassContentFlightOf] dà al contenuto ancorato allo stesso bordo. Pura.
   */
  fun shiftPx(flightLeft: Float, flightRight: Float, barWidthDp: Float, density: Float, rtl: Boolean): Float {
    fun px(dp: Float) = (dp * density).roundToInt()
    val left = if (rtl) px(barWidthDp) - px(leftDp) - px(widthDp) else px(leftDp)
    val right = left + px(widthDp)
    // Il bordo di fine è a destra in LTR e a sinistra in RTL.
    return if (end != rtl) flightRight - right else flightLeft - left
  }
}

/**
 * Il bordo d'ancoraggio comune a [source] e [target] (lo stesso vetro prima e dopo il cambio), o null se il gruppo sta al
 * centro, cambia lato, o il bordo non coincide nei due stati: lì il tasto non ha un bordo che resti suo. Pura.
 */
internal fun fluidToolbarEdgeRide(source: FluidToolbarGroup, target: FluidToolbarGroup): FluidToolbarEdgeRide? {
  if (source.side != target.side) return null
  val end = when (target.side) {
    FluidToolbarSide.Leading -> false
    FluidToolbarSide.Trailing -> true
    FluidToolbarSide.Center -> return null
  }
  fun edge(group: FluidToolbarGroup) = if (end) group.leftDp + group.widthDp else group.leftDp
  if (kotlin.math.abs(edge(source) - edge(target)) > FluidToolbarEdgeToleranceDp) return null
  return FluidToolbarEdgeRide(target.leftDp, target.widthDp, end)
}

/** Due bordi più vicini di così (dp) sono lo stesso bordo: le somme del modello possono differire di un ulp. */
private const val FluidToolbarEdgeToleranceDp = 0.01f

/** Un solo simbolo per tutta la traversata. Le coordinate fisiche includono l'oltrepasso del vetro. */
internal class FluidToolbarTransfer(
  val glass: FluidToolbarGlassId, val leavesSingle: Boolean, val entersSingle: Boolean,
  val startDp: Float, val endDp: Float, val interruptedCenter: Float?,
  val softened: Boolean, private val targetWidthDp: Float, val startItem: FluidToolbarItem,
  /** Il bordo a cui il tasto fermo è ancorato, se la barra lo chiede: vedi [FluidToolbarEdgeRide]. */
  val ride: FluidToolbarEdgeRide? = null,
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

  /**
   * Il centro del glifo in questo fotogramma. [edgeShift] è quanto il bordo d'ancoraggio del vetro è lontano dal suo posto
   * ([FluidToolbarEdgeRide.shiftPx]); vale solo per un posto che coincide nelle due barre e con un bordo da seguire.
   */
  fun center(start: Float, end: Float, lobe: Float?, widthDp: Float?, phase: Float, edgeShift: Float = 0f): Float {
    // Quando il posto coincide in entrambe le barre il glifo resta al suo posto: in alto il rinculo resta al vetro (F4A),
    // in basso il glifo va col bordo del suo vetro, come il «+» di Apple ([ride]).
    if (start == end && (interruptedCenter == null || interruptedCenter == end)) {
      return (if (ride != null) end + edgeShift else end).also { lastCenter = it }
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

  /**
   * Da quanto ritiro (F4A.4) un elemento che sa stringersi prende la forma stretta (F6B.10, la pillola che diventa
   * l'anello): a metà corsa, quando titolo e tasti sono già sfocati e quasi svaniti, così il cambio di forma non si vede
   * mentre la barra se ne va, e uno scorrimento piccolo non lo fa. A occhio, 22 novembre 2026.
   */
  const val RetractingFrom: Float = 0.5f

  /** La scritta dei tasti di testo: quella del corpo, un poco più piena, come i tasti della barra di Apple. */
  @Composable
  fun textStyle(): TextStyle = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Medium)
}

/**
 * Il ritiro della barra (F4A.4) per chi sta **fuori** dal livello che la ritira e deve saperlo lo stesso (F6B.10): la
 * barra in alto della schermata applica il ritiro alla sua riga e dà ai figli [LocalFluidToolbarMinimize] null, perché
 * non lo applichino una seconda volta; questo glielo lascia leggere. Null fuori da una barra che si ritira.
 */
internal val LocalFluidToolbarRetraction = staticCompositionLocalOf<FluidToolbarMinimizeProgress?> { null }

/**
 * Se la barra si sta ritirando (F4A.4): vero da quando il ritiro arriva a [FluidToolbarDefaults.RetractingFrom] a quando
 * torna a zero, così uno scorrimento piccolo che si posa da solo non cambia forma alla pillola. Lo porta nella
 * composizione **un effetto** (`snapshotFlow`), e mai una lettura del ritiro durante la composizione.
 *
 * Perché (F6B.10, secondo giro). Il ritiro è fatto per essere letto in disegno: quello dell'host delle pagine
 * ([dev.antigravity.fluidengine.ui.page.FluidPageBarMinimize]) si calcola dalla lista delle pagine che la barra mostra,
 * che l'host riscrive a ogni sua composizione, e dalla posizione delle pagine, che cambia a ogni fotogramma di un
 * passaggio. Letto nella composizione di questa barra, e in particolare dentro il contenuto di `BoxWithConstraints`
 * (una sotto-composizione, che Compose esegue dopo, mentre misura la barra), legava quella sotto-composizione a uno stato
 * scritto a metà della composizione dell'host: sulla base `a8f4905` la parte esterna della barra riceveva i tasti nuovi
 * («Fatto» al posto di «Strumenti» nel visore) e la sotto-composizione non ripartiva più, così la barra in basso restava
 * ferma (`FluidFileViewerUiTest.swipeAndButtonOpenSameInfoAndBackReturnsToolCapsule`). Con l'effetto la composizione
 * legge solo uno stato suo, scritto dopo il fotogramma e solo quando il «sì o no» cambia.
 */
@Composable
internal fun rememberFluidToolbarRetracting(minimize: FluidToolbarMinimizeProgress?): Boolean {
  // Il valore di partenza si legge una volta, senza osservarlo: una barra che rientra a metà ritiro parte già stretta.
  var retracting by remember(minimize) {
    mutableStateOf(Snapshot.withoutReadObservation { fluidToolbarRetractingAfter(false, minimize?.progress ?: 0f) })
  }
  if (minimize != null) {
    LaunchedEffect(minimize) {
      snapshotFlow { minimize.progress }.collect { progress ->
        retracting = fluidToolbarRetractingAfter(retracting, progress)
      }
    }
  }
  return retracting
}

/** Il passo di [rememberFluidToolbarRetracting]: con l'isteresi, entra a [FluidToolbarDefaults.RetractingFrom], esce a zero. Pura. */
internal fun fluidToolbarRetractingAfter(retracting: Boolean, progress: Float): Boolean =
  if (retracting) progress > 0f else progress >= FluidToolbarDefaults.RetractingFrom

/**
 * Se il titolo agganciato sta **sopra** i vetri della barra (F6B.10, terzo giro): da quando comincia a cedere il posto
 * ([handoff] sopra zero) a quando il vetro di chi lo aveva preso se n'è andato del tutto, cioè una scomparsa del piano
 * ([FluidMotion.PlaneDisappearMillis]) dopo che il titolo è tornato. Il vetro di chi prende il centro (la pillola di
 * attività) mostra la pagina sotto di sé e coprirebbe il titolo: sparirebbe al primo fotogramma della comparsa e
 * tornerebbe di colpo alla fine della scomparsa. Così titolo e pillola si scambiano sfocati sul posto. A riposo falso.
 */
@Composable
internal fun rememberFluidTitleAboveBar(
  handoff: State<Float>,
  /**
   * Vero da quando il centro è occupato (il titolo deve cedere il posto): il titolo sale sopra subito, nella stessa
   * composizione in cui il vetro di chi lo prende nasce, e non un fotogramma dopo, quando [handoff] parte (sul tablet,
   * due fotogrammi senza titolo all'inizio della comparsa).
   */
  hidden: Boolean,
): Boolean {
  val policy = LocalFluidMotionPolicy.current
  val moving by remember(handoff) { derivedStateOf { handoff.value > 0f } }
  val leaving = hidden || moving
  var above by remember { mutableStateOf(leaving) }
  LaunchedEffect(leaving, policy.durationScale) {
    if (leaving) {
      above = true
    } else if (above) {
      delay((FluidMotion.PlaneDisappearMillis * policy.durationScale).toLong().coerceAtLeast(0L))
      above = false
    }
  }
  return above || leaving
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
  keyboardContext: Any? = null,
) {
  val density = LocalDensity.current
  val reducedMotion = LocalFluidMotionPolicy.current.reducedMotion
  val model = fluidToolbarModelOf(declaration, title, keyboardContext)
  val metrics = if (bar == FluidToolbarBar.Bottom || bar == FluidToolbarBar.Keyboard) fluidBottomBarMetrics() else fluidToolbarMetrics()
  // F6B.10: un elemento che sa stringersi (la pillola di attività) prende la sua forma stretta mentre la barra si ritira
  // (F4A.4). Il ritiro entra qui come «sì o no», portato da un effetto: vedi [rememberFluidToolbarRetracting].
  val retracting = rememberFluidToolbarRetracting(
    if (bar == FluidToolbarBar.Keyboard) null else LocalFluidToolbarMinimize.current ?: LocalFluidToolbarRetraction.current,
  )
  BoxWithConstraints(modifier.fillMaxWidth().height(metrics.buttonWidthDp.dp).fluidToolbarMinimizeLayer(towardsBottom = bar == FluidToolbarBar.Bottom)) {
    val width = if (constraints.hasBoundedWidth) maxWidth.value else FluidToolbarUnboundedWidthDp
    val search = LocalToolbarSearch.current.takeUnless { bar == FluidToolbarBar.Keyboard }
    if (search != null && search.owns(bar) && search.hasDeclaration) {
      val pose = search.layout(model, width, metrics)
      SideEffect {
        if (bar != FluidToolbarBar.Bottom) declaration.measured(pose.neighbours)
        onExtent?.invoke(pose.extent(density.density))
      }
      return@BoxWithConstraints
    }
    // Un campo che a riposo non c'è («trova», F6B.6) non tocca questa barra: i suoi fissati restano dove sono.
    val active = bar != FluidToolbarBar.Keyboard && (fluidToolbarSearchTouchesBar(search?.spec)
      ?: (LocalPrincipalSearchActive.current || fluidToolbarSearchTouchesBar(declaration.search) == true))
    // F6B.10: un elemento che sa stringersi (la pillola di attività) prende la sua forma stretta quando non ci sta, e
    // mentre la barra si ritira ([retracting]).
    val resolved = remember(model, width, bar, active, retracting) {
      resolveFluidToolbarFitting(model, width, active, metrics, bar, narrow = retracting)
    }
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
        FluidToolbarGroups(saved[0], shown, reducedMotion = true, interactive = interactive, bar = bar)
      }
    } else {
      FluidToolbarGroups(declaration, resolved, reducedMotion = false, interactive = true, bar = bar)
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
  /** La sede: in basso il tasto che resta al suo posto va col bordo del suo vetro ([FluidToolbarEdgeRide]). */
  bar: FluidToolbarBar = FluidToolbarBar.Top,
  searchField: (@Composable (FluidGlassNamespace) -> Unit)? = null,
) {
    val memory = remember { FluidToolbarMemory() }
    memory.update(resolved, if (reducedMotion) FluidToolbarMotion.Reduced else FluidToolbarMotion.Full, searchActive,
      ridesEdge = bar == FluidToolbarBar.Bottom || bar == FluidToolbarBar.Keyboard)
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
    // Il passaggio a un modale che toglie il pezzo salta l'animazione del piano per due fotogrammi.
    val animation = when {
      menuSource.instant -> FluidGlassAnimation.None
      searchField == null -> FluidGlassAnimation.Default
      else -> ToolbarSearchAnimation
    }
    val menuLease = menuSource.menuLease
    val heldGroup = menuLease?.let { lease -> resolved.groups.firstOrNull { memory.glassIds[it.id] == lease.first } }
    val faces = if (heldGroup != null && menuLease?.second?.groupBounds != null) rememberGraphicsLayer() else null
    // Le facce passano anche all'host del menu, che al ritorno le disegna sopra il suo vetro (S4b, NO 4).
    if (menuLease != null && faces != null) SideEffect { menuLease.second.groupFaces = faces }
    // Anche il tasto singolo resta misurato mentre il menu ne porta il vetro. La corsia si riapre quando
    // l'host restituisce l'ancora, con lo stesso pezzo e la sua registrazione già pronti.
    val mask = if (menuLease == null || heldGroup == null) Modifier else Modifier.drawWithContent {
      val anchor = menuLease.second
      if (!anchor.hidden) {
        drawContent()
        return@drawWithContent
      }
      val start = heldGroup.leftDp.dp.roundToPx().toFloat()
      val width = heldGroup.widthDp.dp.roundToPx().toFloat()
      val left = if (layoutDirection == LayoutDirection.Ltr) start else size.width - start - width
      val halfGap = resolved.metrics.groupSpacingDp.dp.toPx() / 2f
      clipRect(left - halfGap, -size.height, left + width + halfGap, size.height * 2f, ClipOp.Difference) {
        this@drawWithContent.drawContent()
      }
      // Le facce della barra si sfocano all'andata, qui sotto il vetro del menu che nasce; al ritorno le disegna l'host
      // sopra il vetro che torna capsula (`groupFacesAbove`), e alla consegna sono quelle del pezzo vero.
      if (faces != null && !anchor.groupFacesAbove() && anchor.groupProgress() < FluidMotion.MenuGroupFaceProgressSpan) {
        translate(left, (size.height - faces.size.height) / 2f) { drawLayer(faces) }
      }
    }
    FluidGlassContainer(Modifier.fillMaxSize().then(mask), animation = animation) {
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
              menuFaces = faces.takeIf { heldGroup === group },
              modifier = Modifier.align(Alignment.CenterStart).offset(x = group.leftDp.dp),
              keyboardVeil = bar == FluidToolbarBar.Keyboard && group.sharedBackground,
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
  // Un tipo registrato viaggia con la sua faccia; il posto vero, nascosto mentre viaggia, resta quello che suona e dice.
  val typeSlot = entry.type?.resolvedAs(element.item.kind)?.rememberSlot(live = false)
  Box(modifier.requiredSize(FluidMetrics.TouchTarget).graphicsLayer {
    fun center(x: Float) = (if (rtl) resolved.widthDp - x else x) * this.density
    val flight = plane?.flightOf(qualified)
    val u = flight?.phase ?: if (plane?.measurePending == true) 0f else 1f
    val x = if (stationary) {
      val shift = if (flight == null) 0f else
        trip.ride?.shiftPx(flight.bounds.left, flight.bounds.right, resolved.widthDp, this.density, rtl) ?: 0f
      trip.center(restX, restX, null, null, u, shift)
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
    enabled = entry.enabled && entry.type?.acceptsTap != false, role = Role.Button,
  ) {
    haptics.play(FluidHapticEvent.Tap)
    entry.onClick?.invoke()
  }.semantics {
    contentDescription = entry.accessibleName(labels.back, labels.close, labels.confirm)
    if (element.item.selected) selected = true
    element.item.badge?.text?.let { stateDescription = it }
    typeSlot?.describe(this, entry.accessibleName(labels.back, labels.close, labels.confirm))
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
      if (typeSlot != null) typeSlot.Face(endColor, size, element.item.isPrimary)
      else FluidIcon(icon, null, tint = endColor, size = size)
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
internal fun fluidToolbarModelOf(declaration: FluidToolbarDeclaration, title: String?, keyboardContext: Any? = null): FluidToolbarModel {
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
    keyboardContext = keyboardContext,
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
  menuFaces: GraphicsLayer?,
  modifier: Modifier,
  keyboardVeil: Boolean,
) {
  val scheme = MaterialTheme.colorScheme
  val single = group.items.singleOrNull()
  val primary = single?.isPrimary == true
  val primaryEntry = single?.let { declaration.buttons[it.id] }
  val leased = menuSource.anchor(glass)
  val surface = when {
    menuFaces == null && menuSource.modalOwns(glass) -> FluidGlass.Identity
    !group.sharedBackground -> FluidGlass.Identity
    // La principale è un vetro tinto (la tinta è contenuto del vetro: R7 A4); la sua icona è chiara.
    primary -> FluidGlass.Regular.tint(primaryEntry?.let { fluidToolbarTint(it, scheme.primary, scheme.error) } ?: scheme.primary).interactive()
    else -> FluidGlass.Regular.interactive()
  }
  Row(
    modifier
      .then(if (menuFaces == null || leased == null) Modifier else Modifier.drawWithContent {
        menuFaces.record {
          val round = leased.groupClip()
          if (round == null) this@drawWithContent.drawContent()
          else clipPath(Path().apply { addRoundRect(round) }) { this@drawWithContent.drawContent() }
        }
        drawLayer(menuFaces)
      })
      .fluidGlassId(glass, namespace)
      .fluidGlassTransition(fluidToolbarGlassTransition(group))
      // F6B.10, terzo giro: la pillola compare e se ne va sul posto, con la scritta che entra ed esce sfocata (R7).
      .then(if (fluidToolbarSoftMaterialize(group)) Modifier.fluidGlassSoftMaterialize() else Modifier)
      .fluidGlassContentAnchor(when (group.side) {
        FluidToolbarSide.Leading -> FluidGlassContentAnchor.Start
        FluidToolbarSide.Center -> FluidGlassContentAnchor.Center
        FluidToolbarSide.Trailing -> FluidGlassContentAnchor.End
      })
      // Che cosa porta il vetro (F4A.5b): un tondo che cambia simbolo respira, come in Apple.
      .fluidGlassContentKey(fluidToolbarGlassContentKey(group))
      .fluidGlass(surface, FluidCapsuleShape)
      .then(if (keyboardVeil) Modifier.fluidKeyboardVeil(if (primary) scheme.primary else scheme.surface) else Modifier)
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

/** La tab bar possiede già il vetro. Qui riusa i veri slot, la loro semantica, i menu e i tipi registrati. */
@Composable
internal fun FluidToolbarSelectionSlots(
  declaration: FluidToolbarDeclaration,
  resolved: FluidToolbarResolved,
  elements: List<FluidToolbarElement>,
  pane: FluidGlassQualifiedId,
  interactive: Boolean,
) {
  val memory = remember { FluidToolbarMemory() }
  val menu = remember { FluidToolbarMenuSource() }
  val glass = remember(elements) { FluidToolbarGlassId(FluidToolbarGroupId(elements.mapTo(linkedSetOf()) { it.item.id })) }
  for (element in elements) key(element.item.id) {
    val item = element.item
    FluidToolbarSlot(item, element.widthDp, declaration.buttons[item.id], declaration, resolved,
      onGlass = false, glass = glass, memory = memory, namespace = pane.namespace,
      selection = rememberUpdatedState(if (item.selected) 1f else 0f), interactive = interactive,
      entersBlurred = false, menuSource = menu, groupShared = false, groupCount = elements.size, pane = pane)
  }
}

internal class FluidToolbarMenuSource {
  var menuLease by mutableStateOf<Pair<FluidToolbarGlassId, FluidMenuAnchor>?>(null)
    private set
  private var modalLeases by mutableStateOf(emptyMap<FluidToolbarGlassId, FluidMenuAnchor>())
  var instant by mutableStateOf(false)
  var handoff by mutableStateOf(0)
    private set

  fun anchor(glass: FluidToolbarGlassId): FluidMenuAnchor? =
    menuLease?.takeIf { it.first == glass }?.second ?: modalLeases[glass]

  fun modalOwns(glass: FluidToolbarGlassId): Boolean = glass in modalLeases

  fun transferModal(glass: FluidToolbarGlassId, anchor: FluidMenuAnchor, consumed: Boolean) {
    if (consumed) modalLeases = modalLeases + (glass to anchor)
    else if (modalLeases[glass] === anchor) modalLeases = modalLeases - glass
    else return
    instant = true
    handoff++
  }

  fun transfer(glass: FluidToolbarGlassId, anchor: FluidMenuAnchor, consumed: Boolean) {
    if (consumed) menuLease = glass to anchor
    else if (menuLease?.second === anchor) menuLease = null
  }
}

/**
 * Che cosa porta il vetro di [group] (F4A.5b, `fluidGlassContentKey`): le chiavi delle facce dei suoi tasti, nell'ordine.
 * Se cambia e il vetro resta della sua misura (il tondo dell'elenco che diventa la lente), il piano lo fa respirare.
 */
internal fun fluidToolbarGlassContentKey(group: FluidToolbarGroup): Any =
  // Il contenuto del vetro, non lo stato di un tipo registrato: un tasto con esito che riesce non fa respirare il tondo.
  group.items.map { Triple(it.glassContent, it.tintArgb, it.selected) }

/**
 * La forma del vetro per i posti di [group] (F4A.5b): con la variante [FluidToolbarKeptContent.Soft], i tasti che ci sono
 * (cambia quando il gruppo perde o guadagna un tasto, cioè quando il vetro cambia larghezza), e il contenuto che resta si
 * ammorbidisce; con quella di serie null, e niente cambia.
 */
internal fun fluidToolbarSlotShapeKey(group: FluidToolbarGroup, kept: FluidToolbarKeptContent): Any? =
  if (kept == FluidToolbarKeptContent.Soft) group.items.map { it.id } else null

/** Le uscite nascono per lente; se restano, l'identità continua a tenerle ferme. Così la pillola di attività (F6B.10). */
/** Se il vetro di [group] compare con la comparsa morbida (F6B.10): quello di un elemento che la chiede. Pura. */
internal fun fluidToolbarSoftMaterialize(group: FluidToolbarGroup): Boolean = group.items.any { it.kind?.materializes == true }

internal fun fluidToolbarGlassTransition(group: FluidToolbarGroup): FluidGlassTransition =
  if (group.items.any { it.isExit || it.transition == FluidToolbarTransition.Materialize || it.kind?.materializes == true }) {
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
  /** La faccia di un tipo registrato ([FluidToolbarElementType]), con l'inchiostro del posto; prima dell'icona. */
  val typed: (@Composable (Color) -> Unit)? = null,
  val countdown: FluidUndoCountdown? = null,
  val width: Dp = 0.dp,
  val height: Dp = 0.dp,
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
  pane: FluidGlassQualifiedId = FluidGlassQualifiedId(namespace, glass),
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
  // Un tipo registrato (F6B.3a) disegna la sua faccia e cambia stato da sé: lo scambio del posto lo segue solo quando
  // cambia l'identità ([FluidToolbarItem.glassContent]), non lo stato.
  val iconSize = fluidToolbarIconSize(item.role)
  val typeSlot = entry?.type?.resolvedAs(item.kind)?.rememberSlot(live = true)
  val countdown = entry?.undo
  if (interactive && countdown != null) TrackFluidUndoDeadline(countdown.state)
  val undoDescription = if (interactive) rememberFluidUndoDescription(countdown) else null
  val face = FluidToolbarFace(
    key = item.glassContent,
    icon = if (overflow) FluidIcons.MoreHoriz else entry?.icon,
    text = entry?.text,
    color = if (onGlass) color else lerp(color, scheme.primary, selection.value),
    custom = entry?.custom,
    size = iconSize,
    typed = if (typeSlot == null) null else ({ faceColor -> typeSlot.Face(faceColor, iconSize, onGlass) }),
    countdown = countdown,
    width = widthDp.dp,
    height = resolved.metrics.buttonWidthDp.dp,
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
  // Un tasto con esito che lavora non risponde, ma non si vela: l'anello è il suo riscontro (FluidActionState.Working).
  val accepts = entry?.type?.acceptsTap != false
  val textStyle = FluidToolbarDefaults.textStyle()
  // Il trabocco: le azioni nascoste, nel menu «Altro» e, per TalkBack, come azioni del tasto.
  val hidden = if (overflow) resolved.overflow.mapNotNull { declaration.buttons[it.id] } else emptyList()
  val menu = if (overflow && interactive) {
    rememberFluidContextMenu(actions = {
      hidden.flatMap { button ->
        val own = button.contextAction(labels.back, labels.close, labels.confirm)
        // F6B.11: un'azione col suo menu (il tasto diviso, `action(…, onClick)`) porta prima l'azione, poi le voci.
        val menu = button.menu?.invoke()
        when {
          menu == null -> listOf(own)
          button.onClick != null -> listOf(own) + menu
          else -> menu
        }
      }
    })
  } else {
    null
  }
  val declaredMenu = if (entry?.menu != null && interactive) rememberFluidToolbarMenu() else null
  val source = if (declaredMenu != null) rememberFluidToolbarTransitionSource(item.id) else null
  val sourceCanTravel = declaredMenu != null && groupShared && fluidMenuUsesField()
  val active = menuSource.anchor(glass)
  val groupInMenu = active?.groupBounds != null
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
        item.id, pane, modalHandoff,
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
          // Lo stato vivo, letto qui e non quello della composizione (S4b, NO 4): alla consegna del gruppo (la capsula torna
          // al piano nello stesso fotogramma in cui il menu smette di disegnarla) le icone devono essere piene subito, non
          // alla ricomposizione dopo, che le lasciava spente per 2-3 fotogrammi sopra la capsula vuota.
          val live = menuSource.anchor(glass)
          val liveGroup = live?.groupBounds != null
          // Al ritorno le facce le disegna l'host, con l'opacita' della fase (S4g-quater, d): qui restano piene, una volta sola.
          val returning = liveGroup && live?.groupFacesAbove?.invoke() == true
          val phase = if (returning) 0f else live?.groupProgress?.invoke() ?: 0f
          val u = phase / FluidMotion.MenuGroupFaceProgressSpan
          val role = GlassContentRole.Exit
          alpha = when {
            liveGroup -> dim * GlassContentCurves.alpha(role, u, effects != null)
            declaredMenu?.anchor?.hidden == true -> 0f
            else -> dim
          }
          (live ?: declaredMenu?.anchor)?.let { anchor ->
            dev.antigravity.fluidengine.ui.glass.backdrop.GlassFrameDiagnostics.event("toolbar.face", anchor) {
              "phase=$phase alpha=$alpha group=$liveGroup leased=${live != null}"
            }
          }
          val s = if (liveGroup) GlassContentCurves.scale(role, u) else 1f
          scaleX = s
          scaleY = s
          renderEffect = if (!liveGroup || returning) null else effects?.let {
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
    // F6B.11: un'azione con un menu nascosto (R1 N5) fa la sua azione al tocco e apre il menu tenendo premuto; il tasto
    // diviso ha in più la freccia, un bersaglio suo ([FluidToolbarElementType.menuZone]).
    val primaryAction: (() -> Unit)? = entry?.onClick?.takeIf { declaredMenu != null }?.let { action ->
      {
        if (accepts) {
          haptics.play(FluidHapticEvent.Tap)
          action()
        }
      }
    }
    val menuName = entry?.type?.menuZoneLabel ?: label?.let { "${labels.more} ($it)" } ?: labels.more
    val zone = entry?.type?.menuZone?.takeIf { primaryAction != null }
    val targetsShown = interactive && (active == null || declaredMenu?.anchor === active) && item.id !in memory.transfers
    if (targetsShown && zone != null) {
      FluidToolbarSplitTargets(
        width = width, zone = zone, enabled = enabled, hideSemantics = active != null, label = label, menuName = menuName,
        onAction = primaryAction!!, onMenu = openDeclaredMenu,
        describe = { typeSlot?.describe(this, label) },
      )
    }
    // Il bersaglio: almeno 48 dp per lato, centrato sul posto, senza cambiare la misura del vetro.
    if (targetsShown && zone == null) Box(
      Modifier
        .requiredSize(max(width, FluidMetrics.TouchTarget), FluidMetrics.TouchTarget)
        .then(if (active != null) Modifier.clearAndSetSemantics { } else Modifier)
        .then(
          when {
            declaredMenu != null -> Modifier.combinedClickable(
              interactionSource = null,
              indication = null,
              enabled = enabled,
              role = Role.Button,
              onLongClick = openDeclaredMenu,
              onLongClickLabel = if (primaryAction != null) menuName else null,
              onClick = primaryAction ?: openDeclaredMenu,
            )
            menu != null -> Modifier.clickable(
              interactionSource = remember { MutableInteractionSource() },
              indication = null,
              role = Role.Button,
            ) { if (menu.open()) haptics.play(FluidHapticEvent.Open) }
            entry?.onClick != null -> Modifier.clickable(
              interactionSource = remember { MutableInteractionSource() },
              indication = null,
              enabled = enabled && accepts,
              role = Role.Button,
            ) {
              // Anche il doppio tocco di TalkBack: un tasto con esito che lavora non riparte.
              if (accepts) {
                haptics.play(FluidHapticEvent.Tap)
                entry.onClick.invoke()
              }
            }
            else -> Modifier
          },
        )
        .semantics {
          if (label != null) contentDescription = label
          if (item.selected) selected = true
          badge?.text?.let { stateDescription = it }
          // Il tipo registrato dice le sue parole (il tasto con esito: «Invio in corso…», «Inviato», con l'area viva).
          typeSlot?.describe(this, label)
          if (undoDescription != null) fluidUndoDescription(undoDescription)
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

/**
 * I due bersagli del tasto diviso (F6B.11) nel suo posto largo [width]: l'azione, dall'inizio del vetro al filo, e la
 * freccia, dal filo alla fine ([zone] è la zona disegnata della freccia). Disgiunti e ognuno di almeno
 * [FluidMetrics.TouchTarget] ([fluidSplitTargets]): dove la zona disegnata è più stretta il bersaglio esce dal vetro verso
 * il bordo che non tocca l'altro. La pressione lunga sull'azione apre lo stesso menu (R1 N5). Per TalkBack due pulsanti.
 */
@Composable
private fun FluidToolbarSplitTargets(
  width: Dp,
  zone: Dp,
  enabled: Boolean,
  hideSemantics: Boolean,
  label: String?,
  menuName: String,
  onAction: () -> Unit,
  onMenu: () -> Unit,
  describe: SemanticsPropertyReceiver.() -> Unit,
) {
  val targets = fluidSplitTargets(width.value, zone.value, FluidMetrics.TouchTarget.value)
  Box(
    Modifier
      .fluidTargetAt(targets.actionLeft.dp, targets.actionWidth.dp)
      .then(if (hideSemantics) Modifier.clearAndSetSemantics { } else Modifier)
      .combinedClickable(
        interactionSource = null,
        indication = null,
        enabled = enabled,
        role = Role.Button,
        onLongClick = onMenu,
        onLongClickLabel = menuName,
        onClick = onAction,
      )
      .semantics {
        if (label != null) contentDescription = label
        describe()
      },
  )
  Box(
    Modifier
      .fluidTargetAt(targets.menuLeft.dp, targets.menuWidth.dp)
      .then(if (hideSemantics) Modifier.clearAndSetSemantics { } else Modifier)
      .clickable(
        interactionSource = remember { MutableInteractionSource() },
        indication = null,
        enabled = enabled,
        role = Role.Button,
        onClick = onMenu,
      )
      .semantics { contentDescription = menuName },
  )
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
    face.countdown != null -> {
      val fill = rememberFluidUndoFill(face.countdown.state)
      Box(Modifier.size(face.width, face.height).fluidUndoFill(fill, face.color), contentAlignment = Alignment.Center) {
        Text(face.countdown.label, style = textStyle, color = face.color, maxLines = 1, overflow = TextOverflow.Ellipsis)
      }
    }
    face.custom != null -> Box(Modifier.clearAndSetSemantics { }, contentAlignment = Alignment.Center) { face.custom.invoke() }
    face.typed != null -> face.typed.invoke(face.color)
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
    enabled = enabled && onClick != null && type?.acceptsTap != false,
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
