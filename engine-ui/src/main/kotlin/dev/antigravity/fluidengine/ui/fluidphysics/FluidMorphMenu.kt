package dev.antigravity.fluidengine.ui.fluidphysics

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.absoluteOffset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.ProvidableCompositionLocal
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import kotlinx.coroutines.flow.first
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalViewConfiguration
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.toSize
import dev.antigravity.fluidengine.ui.fluid.FluidContextAction
import dev.antigravity.fluidengine.ui.fluid.FluidGlassMenuPanel
import dev.antigravity.fluidengine.ui.fluid.FluidMenuAnchor
import dev.antigravity.fluidengine.ui.fluid.FluidMenuJourney
import dev.antigravity.fluidengine.ui.fluid.FluidMenuCellKind
import dev.antigravity.fluidengine.ui.fluid.FluidMenuContentMap
import dev.antigravity.fluidengine.ui.fluid.FluidMenuElementSize
import dev.antigravity.fluidengine.ui.fluid.FluidMenuLevelClipCeiling
import dev.antigravity.fluidengine.ui.fluid.FluidMenuLevelPeakBlurDp
import dev.antigravity.fluidengine.ui.fluid.FluidMenuLevels
import dev.antigravity.fluidengine.ui.fluid.FluidMenuOutgoingLevel
import dev.antigravity.fluidengine.ui.fluid.FluidMenuStructure
import dev.antigravity.fluidengine.ui.fluid.FluidMenuStructureMetrics
import dev.antigravity.fluidengine.ui.fluid.FluidMenuPanelMetrics
import dev.antigravity.fluidengine.ui.fluid.FluidMetrics
import dev.antigravity.fluidengine.ui.fluid.FluidMenuPlacement
import dev.antigravity.fluidengine.ui.fluid.FluidMenuSelection
import dev.antigravity.fluidengine.ui.fluid.FluidOutsideTapCatcher
import dev.antigravity.fluidengine.ui.fluid.FluidPressFeedback
import dev.antigravity.fluidengine.ui.fluid.LocalFluidGlassModalHostState
import dev.antigravity.fluidengine.ui.fluid.LocalFluidLabels
import dev.antigravity.fluidengine.ui.fluid.GlassBackdropState
import dev.antigravity.fluidengine.ui.fluid.GlassDefaults
import dev.antigravity.fluidengine.ui.fluid.rememberGlassInk
import dev.antigravity.fluidengine.ui.fluid.GlassInkContent
import dev.antigravity.fluidengine.ui.fluid.GlassRole
import dev.antigravity.fluidengine.ui.fluid.LocalFluidMotionPolicy
import dev.antigravity.fluidengine.ui.fluid.fluidMenuContentAlpha
import dev.antigravity.fluidengine.ui.fluid.fluidMenuContentMap
import dev.antigravity.fluidengine.ui.fluid.fluidMenuFaceAlpha
import dev.antigravity.fluidengine.ui.fluid.fluidMenuAnchorInkAlpha
import dev.antigravity.fluidengine.ui.fluid.FluidMenuAnchorFace
import dev.antigravity.fluidengine.ui.fluid.fluidDropInflation
import dev.antigravity.fluidengine.ui.fluid.fluidMenuLiquidContentMap
import dev.antigravity.fluidengine.ui.fluid.fluidMenuPointerAtRest
import dev.antigravity.fluidengine.ui.fluid.FluidMenuLens
import dev.antigravity.fluidengine.ui.fluid.FluidMenuReturnSurface
import dev.antigravity.fluidengine.ui.fluid.fluidMenuUsesField
import dev.antigravity.fluidengine.ui.fluid.LocalFluidMenuPointOfLight
import dev.antigravity.fluidengine.ui.glass.field.glassMenuPointOfLight
import dev.antigravity.fluidengine.ui.fluid.fluidMenuMaterialBlend
import dev.antigravity.fluidengine.ui.fluid.fluidMenuElementSizeOf
import dev.antigravity.fluidengine.ui.fluid.fluidMenuEntries
import dev.antigravity.fluidengine.ui.fluid.fluidMenuLevelEnterAlpha
import dev.antigravity.fluidengine.ui.fluid.fluidMenuLevelEnterBlur
import dev.antigravity.fluidengine.ui.fluid.fluidMenuLevelEnterScale
import dev.antigravity.fluidengine.ui.fluid.fluidMenuLevelExitAlpha
import dev.antigravity.fluidengine.ui.fluid.fluidMenuLevelExitBlur
import dev.antigravity.fluidengine.ui.fluid.fluidMenuLevelExitScale
import dev.antigravity.fluidengine.ui.fluid.fluidMenuLevelWidth
import dev.antigravity.fluidengine.ui.fluid.fluidMenuOpensUpward
import dev.antigravity.fluidengine.ui.fluid.fluidMenuOrderOf
import dev.antigravity.fluidengine.ui.fluid.fluidMenuReverses
import dev.antigravity.fluidengine.ui.fluid.fluidMenuSnapshot
import dev.antigravity.fluidengine.ui.fluid.fluidMenuStructure
import dev.antigravity.fluidengine.ui.fluid.fluidMenuSubmenuEntries
import dev.antigravity.fluidengine.ui.fluid.fluidMenuSubmenuPanel
import dev.antigravity.fluidengine.ui.fluid.fluidMenuPointerRelay
import dev.antigravity.fluidengine.ui.fluid.fluidPressable
import dev.antigravity.fluidengine.ui.fluid.fluidPressedBounds
import dev.antigravity.fluidengine.ui.fluid.glassControlSurface
import dev.antigravity.fluidengine.ui.fluid.rememberFluidMenuPanelMetrics
import dev.antigravity.fluidengine.ui.fluid.rememberFluidMenuLabelMeasure
import dev.antigravity.fluidengine.ui.fluid.rememberFluidMenuStructureMetrics
import dev.antigravity.fluidengine.ui.fluid.rememberFluidMenuSubtitleMeasure
import dev.antigravity.fluidengine.ui.fluid.rememberFluidPressState
import dev.antigravity.fluidengine.ui.fluid.rememberGlassBackdrop
import dev.antigravity.fluidengine.ui.fluid.solveFluidMenuPanel
import dev.antigravity.fluidengine.ui.fluid.unclippedBoundsInRoot
import dev.antigravity.fluidengine.ui.haptics.FluidHapticEvent
import dev.antigravity.fluidengine.ui.haptics.FluidHaptics
import dev.antigravity.fluidengine.ui.haptics.LocalFluidHaptics
import dev.antigravity.fluidengine.ui.fluid.glassControlSurfaceInked
import dev.antigravity.fluidengine.ui.fluid.FluidCapsuleShape
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.unit.IntSize
import kotlinx.coroutines.CoroutineScope
import kotlin.math.roundToInt

/**
 * Il tasto che SI ESPANDE nel proprio menù contestuale — la prima integrazione vera di
 * Fluid-physics nell'interfaccia.
 *
 * Il predecessore ([dev.antigravity.fluidengine.ui.fluid.FluidGlassMenuButton]) fa comparire il
 * pannello *sopra* il tasto: due superfici, una staffetta. Qui la superficie è UNA: la capsula del
 * tasto viaggia fino a essere il pannello del menù, e al congedo torna a essere il tasto. Il
 * contenuto rispetta il contratto: l'etichetta del tasto se ne va con l'inizio del viaggio, le voci
 * del menù arrivano attaccate alla superficie, niente si stira mai.
 *
 * Dalla 3.0 (fase 7) il menu si sceglie **col dito che l'ha aperto**: tieni premuto, il menu si apre
 * e il dito ci passa sopra — una goccia di vetro lo segue di voce in voce, un tick per voce — e al
 * rilascio su una voce la sceglie. Se il dito si solleva senza essersi mosso, o fuori dalle voci, il
 * menu RESTA aperto e si sceglie con un tocco. Il viaggio, il pannello, la goccia e l'aptica sono
 * quelli di ogni menu dell'engine (`FluidMenuJourney`, `FluidGlassMenuPanel`).
 *
 * Architettura portale/host come i modali della casa: l'ancora ([FluidMorphMenuButton]) vive nel
 * layout e riporta i propri limiti; la superficie che si trasforma vive in un [FluidMorphMenuHost]
 * montato alla radice — un menù deve crescere oltre i limiti del proprio genitore, e l'unico posto
 * dove il vetro può farlo restando vetro è dentro la composizione, non in una finestra.
 */
@Stable
class FluidMorphMenuState internal constructor() {

  internal var actions by mutableStateOf<List<FluidContextAction>>(emptyList())

  /** Il tasto da cui il menu in scena e' nato: si nasconde mentre il menu c'e', e ci si torna. */
  internal var anchor by mutableStateOf<FluidMenuAnchor?>(null)

  /** Il pannello, in coordinate dell'host; e le sue celle. */
  internal var placement by mutableStateOf<MorphMenuPlacement?>(null)
    private set

  /** Vero dal gesto d'apertura alla richiesta di congedo. */
  var isOpen by mutableStateOf(false)
    internal set

  /** Vero finché c'è qualcosa in scena, viaggio di ritorno compreso: l'ancora resta nascosta. */
  var isOnScreen by mutableStateOf(false)
    internal set

  internal val physics = FluidPhysicsState(FluidForm.circle(Offset(1f, 1f), 1f))
  internal val journey = FluidMenuJourney()
  internal val selection = FluidMenuSelection()
  internal val levels = FluidMenuLevels()

  /**
   * Dove sta l'host e quanto e' grande, e le misure del pannello: le scrive l'host a ogni layout e a
   * ogni composizione, da un nodo sempre composto. E' questo che rende l'apertura SINCRONA: il gesto
   * risolve il pannello, installa il viaggio e nasconde il tasto nello stesso istante, e il primo
   * fotogramma dell'host e' gia' la sagoma sul tasto. Prima l'host ripartiva da un rettangolo vuoto a
   * ogni apertura e aspettava il proprio layout: un fotogramma senza ne' tasto ne' menu.
   */
  internal var hostOrigin: Offset? = null
  internal var hostSize: Size? = null
  internal var metrics: FluidMenuPanelMetrics? = null

  /** La larghezza di un'etichetta nel tipo delle voci, e la densita': il pannello si allarga per le voci lunghe (3.0.1). */
  internal var labelWidth: ((String) -> Float)? = null
  internal var subtitleWidth: ((String) -> Float)? = null
  internal var structureMetrics: ((FluidMenuElementSize) -> FluidMenuStructureMetrics)? = null
  internal var density: Float = 1f
  internal var liquidMenu: Boolean = false

  /** L'ancora del vecchio [open] pubblico, che un'ancora sua non ce l'ha. */
  private val publicAnchor = FluidMenuAnchor()

  /** Chiede il viaggio di ritorno. Il tasto riappare quando la superficie è tornata capsula. */
  fun dismiss() {
    if (!isOpen) return
    isOpen = false
    // Un congedo a dito giu' (il back durante il trascinamento): il dito non e' piu' del menu.
    anchor?.handOff?.sink = null
    selection.cancel()
    if (levels.driving) {
      journey.install(physics)
      levels.driving = false
    }
    journey.retarget(0f)
  }

  /**
   * Apre il menu dalla sagoma di [bounds] (pixel della radice). Il gesto e' affare del chiamante: un
   * tasto il cui UNICO mestiere e' il menu puo' aprirlo al tocco — pubblico dalla 1.13.0 per quello
   * (dalla 3.0 [FluidMorphMenuButton] lo fa da se', con `opensOnTap`). Chi chiama da un'ancora propria
   * deve anche nascondersi mentre [isOnScreen] e' vero, come fa quella di casa. Senza il dito: il menu
   * si apre e si sceglie con un tocco.
   */
  fun open(
    bounds: Rect,
    text: String?,
    icon: ImageVector?,
    items: List<FluidContextAction>,
    /** The anchor's own face; null falls back to text and icon. Must be inert. */
    content: (@Composable () -> Unit)? = null,
  ) {
    publicAnchor.restBounds = bounds
    publicAnchor.pressedBounds = bounds
    publicAnchor.face = content ?: { MorphMenuAnchorLabel(text, icon, MaterialTheme.colorScheme.onSurface) }
    openFrom(publicAnchor, items, handOff = false)
  }

  /**
   * L'apertura, sincrona: risolve il pannello con le misure dell'host, installa il viaggio, nasconde
   * il tasto e — se [handOff] — gli prende il dito. Falso se non si apre (niente voci, un altro menu
   * in scena, un host mai composto): il chiamante allora non vibra.
   *
   * Riaprire dallo stesso tasto un menu che si sta chiudendo e' un'inversione: il viaggio torna verso
   * il pannello da dove si trova, con la sua velocita'.
   */
  internal fun openFrom(anchor: FluidMenuAnchor, items: List<FluidContextAction>, handOff: Boolean): Boolean {
    if (items.isEmpty() || isOpen) return false
    if (isOnScreen && this.anchor !== anchor) return false
    val list = fluidMenuSnapshot(items)
    val placed = resolve(anchor, items, list, parent = -1, previous = -1, swapping = false) ?: return false
    val reopening = isOnScreen
    actions = items
    levels.reset()
    this.anchor = anchor
    selection.cancel()
    applyPlacement(placed, fresh = !reopening)
    anchor.consume(true)
    isOnScreen = true
    isOpen = true
    journey.retarget(1f)
    if (handOff) anchor.handOff.attach(selection)
    return true
  }

  internal fun resolve(
    anchor: FluidMenuAnchor,
    items: List<FluidContextAction>,
    list: List<FluidContextAction>,
    parent: Int,
    previous: Int,
    swapping: Boolean,
  ): MorphMenuPlacement? {
    if (list.isEmpty()) return null
    val m = metrics ?: return null
    val origin = hostOrigin ?: return null
    val host = hostSize ?: return null
    val elementSize = fluidMenuElementSizeOf(items)
    val sm = structureMetrics?.invoke(elementSize) ?: return null
    val source = anchor.restBounds.translate(-origin.x, -origin.y)
    fun width(cells: FluidMenuStructure): Float = fluidMenuLevelWidth(
      cells.entries, m, labelWidth ?: { 0f }, subtitleWidth ?: { 0f }, density, host.width,
    )
    val written = fluidMenuStructure(fluidMenuEntries(list, elementSize, reversed = false), sm)
    val rootFrame = solveFluidMenuPanel(
      source, host, width(written), written.height, m.gapPx, m.marginPx, FluidMenuPlacement.AboveOrBelow,
    )
    val up = fluidMenuOpensUpward(source, rootFrame)
    val reverse = fluidMenuReverses(fluidMenuOrderOf(items), up, fromButton = true)
    val root = if (reverse) fluidMenuStructure(fluidMenuEntries(list, elementSize, reversed = true), sm) else written
    fun level(index: Int): MorphMenuLevel {
      val action = list.getOrNull(index)?.takeIf { it.children.isNotEmpty() } ?: return MorphMenuLevel(root, rootFrame)
      val cells = fluidMenuStructure(fluidMenuSubmenuEntries(action, reverse), sm)
      return MorphMenuLevel(cells, fluidMenuSubmenuPanel(rootFrame, width(cells), cells.height, host, m.marginPx, up))
    }
    return MorphMenuPlacement(level(parent), if (swapping) level(previous) else null, elementSize)
  }

  internal fun applyPlacement(placed: MorphMenuPlacement, fresh: Boolean = false) {
    val source = anchor ?: return
    val origin = hostOrigin ?: return
    val host = hostSize ?: return
    val m = metrics ?: return
    val (cells, panel) = placed.current
    placement = placed
    journey.prepare(
      anchor = source, hostOrigin = origin, panel = panel, radius = m.radiusPx,
      inflation = fluidDropInflation(panel.width, host.width, m.edgeMinPx),
      freshStart = fresh, liquidMenu = liquidMenu,
      // La nascita di Apple anche qui (sessione contestuale, fase tasto): il tasto come B1 (a).
      appleBirth = liquidMenu,
    )
    val from = levels.fromPanel
    if (levels.morphing && from != null) {
      physics.driveExternally(
        from = FluidForm.Slab(from, FluidCornerRadii.all(m.radiusPx)),
        to = FluidForm.Slab(panel, FluidCornerRadii.all(m.radiusPx)),
        progress = { levels.morph.value },
      )
      levels.driving = true
    } else {
      journey.install(physics)
      levels.driving = false
    }
    selection.geometry = cells.geometry(panel.translate(origin.x, origin.y))
  }

  /** Le voci madri e l'intestazione cambiano livello nello stesso vetro. */
  internal fun pick(index: Int, haptics: FluidHaptics, scope: CoroutineScope, reducedMotion: Boolean) {
    val current = placement?.current ?: return
    val cell = current.cells.entries.getOrNull(index) ?: return
    if (!isOpen || levels.swapping || !cell.enabled) return
    haptics.play(FluidHapticEvent.Tap)
    when {
      cell.kind == FluidMenuCellKind.Header -> levels.change(-1, current.frame, scope, reducedMotion)
      cell.opensSubmenu -> levels.change(cell.source, current.frame, scope, reducedMotion)
      cell.action.keepsMenuOpen -> cell.action.onClick()
      else -> {
        dismiss()
        cell.action.onClick()
      }
    }
  }

  /** Il ritorno e' finito: il tasto torna a disegnarsi nello stesso istante in cui l'host smonta. */
  internal fun finishClose() {
    if (isOpen) return
    anchor?.consume(false)
    anchor = null
    placement = null
    isOnScreen = false
    journey.clearAnchor()
  }
}

internal data class MorphMenuLevel(val cells: FluidMenuStructure, val frame: Rect)

internal data class MorphMenuPlacement(
  val current: MorphMenuLevel,
  val previous: MorphMenuLevel?,
  val elementSize: FluidMenuElementSize,
)

@Composable
fun rememberFluidMorphMenuState(): FluidMorphMenuState = remember { FluidMorphMenuState() }

/**
 * Lo stato del menu che si trasforma dell'app, per chi sta lontano dalla radice (3.0.1).
 *
 * L'host ([FluidMorphMenuHost]) sta alla radice e il tasto ([FluidMorphMenuButton]) in fondo a una
 * schermata: lo stato doveva attraversare ogni livello in mezzo come parametro, e Pampa Store si era fatto un
 * suo `CompositionLocal` per non farlo. L'app lo fornisce una volta, accanto a `LocalFluidGlassModalHostState`:
 *
 * ```kotlin
 * val morphMenu = rememberFluidMorphMenuState()
 * CompositionLocalProvider(LocalFluidMorphMenuState provides morphMenu) {
 *   App()
 *   FluidMorphMenuHost(state = morphMenu, backdrop = backdrop)
 * }
 * ```
 *
 * e da li' un [FluidMorphMenuButton] lo prende da solo (`state` di serie), e chi apre il menu da un'altra
 * parte — una scorciatoia, un tasto della barra — lo chiede qui ([FluidMorphMenuState.open],
 * [FluidMorphMenuState.dismiss]). Null senza un fornitore.
 */
val LocalFluidMorphMenuState: ProvidableCompositionLocal<FluidMorphMenuState?> = staticCompositionLocalOf { null }

/**
 * L'ancora: una capsula di vetro coi gesti dei tasti — tocco per l'azione, tenuto premuto diventa il
 * menù (un `Open`, uno solo, e il dito passa al menu). Mentre il menù è in scena l'ancora non disegna
 * niente: al suo posto, allo stesso pixel, sta la superficie dell'host — la stessa capsula, che parte
 * per il viaggio.
 *
 * @param opensOnTap il tocco apre il menu invece di fare [onClick] (che allora non viene chiamato):
 *   per un tasto il cui unico mestiere e' il menu ("Ordina", "Altro"). Il tenuto premuto apre in
 *   ogni caso. Dalla 3.0.
 * @param state di serie quello di [LocalFluidMorphMenuState] (3.0.1); senza un fornitore ne' uno passato, il
 *   tasto fa il suo [onClick] e il menu non si apre (non c'e' un host che lo disegni).
 */
@Composable
@Suppress("ModifierParameter") // firma pubblicata: la posizione di `modifier` e' compatibilita' sorgente (le protegge Compat*), spostarla rompe le chiamate posizionali
fun FluidMorphMenuButton(
  state: FluidMorphMenuState = LocalFluidMorphMenuState.current ?: rememberFluidMorphMenuState(),
  actions: () -> List<FluidContextAction>,
  onClick: () -> Unit,
  modifier: Modifier = Modifier,
  text: String? = null,
  icon: ImageVector? = null,
  backdrop: GlassBackdropState,
  enabled: Boolean = true,
  opensOnTap: Boolean = false,
) {
  val contentColor = MaterialTheme.colorScheme.onSurface
  // Una pressione sola per il click e per il vetro, come FluidGlassButton: la capsula cresce e si
  // illumina sullo stesso orologio con cui l'azione viene confermata.
  val press = rememberFluidPressState(FluidPressFeedback.Expand)
  val anchor = remember { FluidMenuAnchor() }
  val density = LocalDensity.current.density
  val haptics = LocalFluidHaptics.current
  val tint = GlassDefaults.controlTint()
  // F2.3b: il tasto possiede la sua etichetta e gira come i tasti di vetro; la sua faccia in viaggio dentro l'host resta
  // dell'inchiostro del tema (si spegne nel primo terzo del viaggio).
  val buttonInk = rememberGlassInk()

  SideEffect {
    // G1 (C-R2): la faccia in viaggio segue l'inchiostro del tasto ([FluidMenuAnchor.ink]), come la sua etichetta qui.
    anchor.face = { MorphMenuAnchorLabel(text = text, icon = icon, color = LocalContentColor.current) }
    anchor.ink = buttonInk
    anchor.tintFrom = tint
    anchor.opticsFrom = GlassDefaults.buttonOptics()
  }
  DisposableEffect(state, anchor) {
    onDispose {
      // Il tasto esce dalla composizione col suo menu aperto: il menu se ne va con lui.
      if (state.anchor === anchor) state.dismiss()
      anchor.hidden = false
    }
  }

  // Il click lungo: la sagoma parte dal tasto COME LO STA GONFIANDO il dito, e il dito passa al menu.
  // Un Open solo, e solo se il menu si e' aperto davvero.
  val openHeld: () -> Unit = {
    anchor.pressedBounds = fluidPressedBounds(anchor.restBounds, press, density)
    if (state.openFrom(anchor, actions(), handOff = true)) haptics.play(FluidHapticEvent.Open)
  }
  // Il tocco, per chi apre al tocco: il dito e' gia' su, il menu si sceglie toccando. Il colpo lo
  // suona il click del tasto (`haptic = Open` qui sotto).
  val openTapped: () -> Unit = {
    anchor.pressedBounds = anchor.restBounds
    state.openFrom(anchor, actions(), handOff = false)
  }

  Row(
    modifier = modifier
      // Primo, il relè: vede il dito prima di qualunque gesto del tasto e non dipende da cosa quei
      // gesti consumano.
      .fluidMenuPointerRelay(anchor.handOff)
      .onGloballyPositioned { anchor.restBounds = it.unclippedBoundsInRoot() }
      // Sparizione secca, non dissolvenza, e nel disegno: la superficie dell'host appare nello
      // stesso fotogramma allo stesso posto con la stessa sagoma — è un cambio di proprietario,
      // non un'uscita. Niente alpha di un layer sopra il vetro.
      .drawWithContent { if (!anchor.hidden) drawContent() }
      // interactive = true, di nuovo: senza, il tasto era l'unico della fila a non accendersi
      // sotto il dito — "ha perso quella capacità", ed è vero.
      .glassControlSurfaceInked(
        backdrop = backdrop,
        shape = FluidCapsuleShape,
        tint = tint,
        optics = GlassDefaults.buttonOptics(),
        selected = false,
        interactive = true,
        depth = { 1f },
        frozen = { false },
        heldWhileMoving = { false },
        pressState = press,
        ink = buttonInk,
      )
      .fluidPressable(
        pressState = press,
        onClick = if (opensOnTap) openTapped else onClick,
        onLongClick = openHeld,
        enabled = enabled,
        role = Role.Button,
        // Il tocco e' il click di un tasto (PressClick), o l'apertura se apre al tocco. Il click
        // lungo non ha un colpo suo: lo suona `openHeld`, solo se apre — prima erano GestureStart
        // del tasto piu' Open, due colpi per un momento.
        haptic = if (opensOnTap) FluidHapticEvent.Open else FluidHapticEvent.Tap,
        longClickHaptic = null,
      )
      // La capsula d'origine ha l'altezza dei tasti della barra.
      .height(FluidMetrics.GlassButtonLens)
      .padding(horizontal = 20.dp),
    horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally),
    verticalAlignment = Alignment.CenterVertically,
  ) {
    GlassInkContent(buttonInk, contentColor) {
      MorphMenuAnchorLabel(text = text, icon = icon, color = LocalContentColor.current)
    }
  }
}

/**
 * L'host: da montare alla radice, sopra il contenuto e sotto niente. A riposo compone un nodo vuoto
 * (niente gesti, niente disegno) che tiene le misure per l'apertura sincrona.
 */
@Composable
fun FluidMorphMenuHost(
  state: FluidMorphMenuState,
  backdrop: GlassBackdropState,
  modifier: Modifier = Modifier,
) {
  state.liquidMenu = fluidMenuUsesField()
  val pointOfLight = LocalFluidMenuPointOfLight.current
  val reducedMotion = LocalFluidMotionPolicy.current.reducedMotion
  val density = LocalDensity.current
  val haptics = LocalFluidHaptics.current
  val touchSlop = LocalViewConfiguration.current.touchSlop
  val metrics = rememberFluidMenuPanelMetrics()
  val labelWidth = rememberFluidMenuLabelMeasure()
  val subtitleWidth = rememberFluidMenuSubtitleMeasure()
  val rowMetrics = rememberFluidMenuStructureMetrics(metrics, FluidMenuElementSize.Small)
  val mediumMetrics = rememberFluidMenuStructureMetrics(metrics, FluidMenuElementSize.Medium)
  val scope = rememberCoroutineScope()
  val levels = state.levels
  levels.subscribe()
  state.physics.reducedMotion = reducedMotion
  state.physics.density = density.density

  SideEffect {
    state.metrics = metrics
    state.labelWidth = labelWidth
    state.subtitleWidth = subtitleWidth
    state.structureMetrics = { if (it == FluidMenuElementSize.Medium) mediumMetrics else rowMetrics }
    state.density = density.density
    state.selection.touchSlop = touchSlop
    state.selection.onTick = { haptics.play(FluidHapticEvent.Tick) }
    state.selection.onPick = { index -> state.pick(index, haptics, scope, reducedMotion) }
  }
  val currentAnchor = state.anchor
  if (state.isOpen && currentAnchor != null) {
    val source = state.actions
    val list = fluidMenuSnapshot(source)
    val parent = levels.openParent
    val previous = levels.previousParent
    val swapping = levels.swapping
    val morphing = levels.morphing
    val placed = remember(state, currentAnchor, source, list, parent, previous, swapping, morphing) {
      state.resolve(currentAnchor, source, list, parent, previous, swapping)
    }
    SideEffect {
      if (placed != null && state.placement !== placed) state.applyPlacement(placed)
    }
  }

  // Il conducente del viaggio: un verso alla volta, dal valore e dalla velocita' di adesso. Il
  // ritorno finito rimette il tasto e smonta, nello stesso istante.
  LaunchedEffect(state, state.journey.target, reducedMotion) {
    state.journey.run(reducedMotion)
    if (state.journey.target == 0f) {
      // La goccia arriva dopo la molla (3.0): il tasto torna quando anche la coda si e' posata sulla
      // sua sagoma, non un fotogramma prima — se no il tasto vero comparirebbe sopra una goccia ancora
      // in movimento.
      snapshotFlow { state.physics.liquid.quiet }.first { it }
      state.finishClose()
    }
  }

  // Il back chiude il menu prima di qualunque pagina. Il gestore entra in composizione con
  // l'apertura, non con l'host: sul dispatcher risponde il gestore abilitato registrato per ultimo, e
  // l'host sta alla radice, composto prima di tutto quello che si apre dopo — un `FluidPageHost`
  // annidato (le sottopagine delle impostazioni), il `BackHandler` di una pagina. Sempre composto, si
  // registrava una volta sola all'avvio e restava sotto di loro: il back toglieva la sottopagina e il
  // menu restava in scena. Ora fa come i modali, che compongono il loro gestore con la voce aperta. Il
  // congedo lo toglie: un secondo back, anche durante il ritorno, va alla pagina.
  if (state.isOpen) BackHandler {
    if (levels.openParent < 0) state.dismiss()
    else if (!levels.swapping) levels.change(-1, state.placement?.current?.frame, scope, reducedMotion)
  }

  // Un tocco fuori dal menu, OVUNQUE sullo schermo, lo chiude (3.0). L'acchiappa-tocco qui sotto
  // copre solo i limiti dell'host: montato in un palco (la galleria) o in una parte della pagina, un
  // tocco sopra il palco non chiudeva niente. Fuori dai limiti i tocchi li prende l'host dei modali,
  // l'unico alla radice, con quattro fasce attorno all'host; senza host dei modali resta com'era.
  val modalHost = LocalFluidGlassModalHostState.current
  val outside = remember(state) { FluidOutsideTapCatcher { state.dismiss() } }
  val open = state.isOpen
  DisposableEffect(modalHost, outside, open) {
    if (modalHost != null && open) {
      val origin = state.hostOrigin ?: Offset.Zero
      val size = state.hostSize ?: Size.Zero
      outside.hole = Rect(origin, size)
      modalHost.outsideCatchers.add(outside)
    }
    onDispose { modalHost?.outsideCatchers?.remove(outside) }
  }

  // Vetro su vetro (2.13.0): il menu' pubblica il proprio materiale per il vetro che ci sta dentro —
  // la goccia. Il contenuto e' in nodi fratelli della superficie, quindi lo dichiara con
  // `glassContainer`. Si registra solo mentre la goccia lo campiona.
  val menuGlass = rememberGlassBackdrop()
  // F2.3b: il pannello possiede le sue voci e scurisce in continuo (un grande, `Modal`); le voci lo seguono. La faccia
  // del tasto in viaggio resta del tasto.
  val menuInk = rememberGlassInk()

  // Il colore del testo sul pannello (3.0.1): l'host sta alla radice, fuori da una `Surface`, dove
  // `LocalContentColor` vale nero. Le voci hanno il loro colore, ma una faccia del tasto o un'icona
  // senza `tint` ereditano questo.
  CompositionLocalProvider(LocalContentColor provides MaterialTheme.colorScheme.onSurface) {
    Box(
      modifier = modifier
        .fillMaxSize()
        .onGloballyPositioned {
          state.hostOrigin = it.localToRoot(Offset.Zero)
          state.hostSize = it.size.toSize()
        },
    ) {
      val anchor = state.anchor
      val placed = state.placement
      val origin = state.hostOrigin ?: Offset.Zero
      if (state.isOnScreen && anchor != null && placed != null) {
        val (cells, panel) = placed.current
        val progress: () -> Float = { state.journey.progress.value }

        // L'acchiappa-tocco: la pagina resta a colori — è il bordo del vetro a separare, non un velo.
        if (state.isOpen) {
          Box(
            modifier = Modifier
              .fillMaxSize()
              .pointerInput(state) { detectTapGestures(onTap = { state.dismiss() }) },
          )
        }

        FluidMenuReturnSurface(state.journey.controlledReturn) {
          Modifier
            .fillMaxSize()
            .fluidPhysicsSurfaceInked(
              ink = menuInk,
              state = state.physics,
              backdrop = backdrop,
              tint = GlassDefaults.panelTint(),
              role = GlassRole.Modal,
              // Il pannello flottante, compatto: un menu di poche righe.
              optics = GlassDefaults.panelOptics(compact = true),
              // La pelle e' parte del viaggio: alla partenza la sagoma E' il tasto (la sua pellicola e
              // la sua lente, cupola e frangia comprese), al 60% del viaggio e' gia' il pannello. Al
              // ritorno la strada inversa: il tasto riappare senza salti di colore ne' di vetro.
              tintFrom = anchor.tintFrom ?: GlassDefaults.controlTint(),
              tintBlend = { fluidMenuMaterialBlend(progress()) },
              opticsFrom = anchor.opticsFrom ?: GlassDefaults.buttonOptics(),
              opticsBlend = { fluidMenuMaterialBlend(progress()) },
              exports = menuGlass,
              // G1 (C-R2): il pannello nasce col lato, la misura e l'inchiostro del tasto.
              adoptFrom = anchor.ink?.state,
            )
            .then(if (state.journey.liquidMenu && pointOfLight && android.os.Build.VERSION.SDK_INT >= 33) {
              Modifier.glassMenuPointOfLight(state.journey, state.physics)
            } else Modifier)
        }

        // La faccia del tasto: parte col viaggio d'andata, seguendo la sagoma, e torna con quello di
        // ritorno. Spenta dal 35% del viaggio: le voci arrivano dal 25%, mai leggibili insieme.
        val anchorLocal = anchor.restBounds.translate(-origin.x, -origin.y)
        MorphMenuFrameBox(frame = anchorLocal) {
          Row(
            modifier = Modifier
              .fillMaxSize()
              .graphicsLayer {
                alpha = state.journey.faceAlpha(state.physics) * fluidMenuAnchorInkAlpha(anchor)
                val map = fluidMenuContentMap(target = anchorLocal, current = state.physics.contentBoundsNow())
                translationX = map.translation.x
                translationY = map.translation.y
                scaleX = map.scale
                scaleY = map.scale
              },
            horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally),
            verticalAlignment = Alignment.CenterVertically,
          ) {
            FluidMenuAnchorFace(anchor)
          }
        }

        // Le voci: arrivano attaccate alla superficie e, al ritorno, la seguono finche' si spengono.
        GlassInkContent(menuInk, LocalContentColor.current) {
          val panelInLayout = panel.translate(
            panel.left.toInt() - panel.left,
            panel.top.toInt() - panel.top,
          )
          val lens = if (state.journey.liquidMenu) FluidMenuLens(state.journey, state.physics, panelInLayout) else null
          val baseAlpha: () -> Float = {
            val alpha = if (lens != null) state.journey.lensInkAlpha() else
              fluidMenuContentAlpha(progress(), closing = state.journey.closing)
            alpha * (menuInk.state?.inkAlpha() ?: 1f)
          }
          val blurAvailable = android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.S
          // Dove si disegnano le voci in questo istante: la stessa mappa per il layer e per il dito che sceglie.
          val contentMapNow: () -> FluidMenuContentMap = {
            if (levels.driving) {
              FluidMenuContentMap(Offset.Zero, if (levels.swapping) fluidMenuLevelEnterScale(levels.swap.value) else 1f)
            } else {
              lens?.contentMap() ?: fluidMenuLiquidContentMap(panel, state.physics.contentBoundsNow(), baseAlpha())
            }
          }
          // Il dito mira alle voci che vede (sessione contestuale, fase tasto); la geometria delle celle parla la radice.
          SideEffect {
            val origin = state.hostOrigin ?: Offset.Zero
            state.selection.pointerMap = { root ->
              if (levels.driving) root else fluidMenuPointerAtRest(root - origin, panelInLayout, contentMapNow()) + origin
            }
          }
          val tileLabels = placed.elementSize == FluidMenuElementSize.Medium
          val outgoing = placed.previous?.let { (oldCells, oldFrame) ->
            FluidMenuOutgoingLevel(
              structure = oldCells,
              offset = IntOffset(oldFrame.left.toInt() - panel.left.toInt(), oldFrame.top.toInt() - panel.top.toInt()),
              size = IntSize(oldFrame.width.roundToInt(), oldFrame.height.roundToInt()),
              tileLabels = tileLabels,
              alpha = { baseAlpha() * fluidMenuLevelExitAlpha(levels.swap.value, blurAvailable) },
              scale = { fluidMenuLevelExitScale(levels.swap.value) },
              blurDp = { fluidMenuLevelExitBlur(levels.swap.value) * FluidMenuLevelPeakBlurDp },
            )
          }
          FluidGlassMenuPanel(
            actions = cells.entries.map { it.action },
            selection = state.selection,
            layout = null,
            panelMaterial = menuGlass,
            pageBackdrop = backdrop,
            // F2.3b: con la dissolvenza dell'inchiostro attorno al passaggio di lato.
            contentAlpha = {
              val levelAlpha = if (levels.swapping) fluidMenuLevelEnterAlpha(levels.swap.value, blurAvailable) else 1f
              baseAlpha() * levelAlpha
            },
            contentMap = contentMapNow,
            onPick = { index -> state.pick(index, haptics, scope, reducedMotion) },
            modifier = Modifier.frame(panel),
            paneTitle = LocalFluidLabels.current.actions,
            lens = lens,
            // Le voci non escono mai dal vetro mentre si forma o si ritira: ritagliate sul corpo.
            travelClip = {
              val clipProgress = if (levels.driving) levels.morph.value.coerceAtMost(FluidMenuLevelClipCeiling) else progress()
              state.physics.travelClipIn(panelInLayout, clipProgress)
            },
            structure = cells,
            tileLabels = tileLabels,
            levelBlurDp = { if (levels.swapping) fluidMenuLevelEnterBlur(levels.swap.value) * FluidMenuLevelPeakBlurDp else 0f },
            outgoing = outgoing,
          )
        }
      }
    }
  }
}

@Composable
private fun MorphMenuAnchorLabel(text: String?, icon: ImageVector?, color: Color) {
  if (icon != null) {
    Icon(imageVector = icon, contentDescription = null, tint = color, modifier = Modifier.size(20.dp))
  }
  if (text != null) {
    Text(
      text = text,
      style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.SemiBold),
      color = color,
    )
  }
}

@Composable
private fun MorphMenuFrameBox(frame: Rect, content: @Composable () -> Unit) {
  Box(modifier = Modifier.frame(frame), contentAlignment = Alignment.Center) {
    content()
  }
}

/** Posa un nodo esattamente su [frame], in pixel dell'host. */
@Composable
private fun Modifier.frame(frame: Rect): Modifier {
  val density = LocalDensity.current
  return this
    .absoluteOffset { IntOffset(frame.left.toInt(), frame.top.toInt()) }
    .size(
      width = with(density) { frame.width.toDp() },
      height = with(density) { frame.height.toDp() },
    )
}

/**
 * Dove sta il pannello: sopra l'ancora se lo spazio basta — un menù che nasce da un tasto in
 * basso cresce verso l'alto — altrimenti sotto, sempre dentro i margini dell'host, col centro
 * orizzontale il più vicino possibile al tasto. Pura e testabile; dalla 3.0 e' il caso
 * [FluidMenuPlacement.AboveOrBelow] di `solveFluidMenuPanel`, condiviso con gli altri menu.
 */
internal fun solveMorphMenuPanel(
  anchor: Rect,
  host: Rect,
  itemCount: Int,
  rowHeightPx: Float,
  panelWidthPx: Float,
  panelPaddingPx: Float,
  gapPx: Float,
  marginPx: Float,
): Rect = solveFluidMenuPanel(
  anchor = anchor,
  host = Size(host.width, host.height),
  width = panelWidthPx,
  height = itemCount * rowHeightPx + panelPaddingPx * 2f,
  gapPx = gapPx,
  marginPx = marginPx,
  placement = FluidMenuPlacement.AboveOrBelow,
)

