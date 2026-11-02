package dev.antigravity.fluidengine.ui.fluidphysics

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
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
import dev.antigravity.fluidengine.ui.fluid.FluidMenuPanelLayout
import dev.antigravity.fluidengine.ui.fluid.FluidMenuPanelMetrics
import dev.antigravity.fluidengine.ui.fluid.FluidMenuPlacement
import dev.antigravity.fluidengine.ui.fluid.FluidMenuSelection
import dev.antigravity.fluidengine.ui.fluid.FluidOutsideTapCatcher
import dev.antigravity.fluidengine.ui.fluid.FluidPressFeedback
import dev.antigravity.fluidengine.ui.fluid.LocalFluidGlassModalHostState
import dev.antigravity.fluidengine.ui.fluid.GlassBackdropState
import dev.antigravity.fluidengine.ui.fluid.GlassDefaults
import dev.antigravity.fluidengine.ui.fluid.GlassRole
import dev.antigravity.fluidengine.ui.fluid.LocalFluidMotionPolicy
import dev.antigravity.fluidengine.ui.fluid.fluidMenuContentAlpha
import dev.antigravity.fluidengine.ui.fluid.fluidMenuContentMap
import dev.antigravity.fluidengine.ui.fluid.fluidMenuFaceAlpha
import dev.antigravity.fluidengine.ui.fluid.fluidMenuGeometryOf
import dev.antigravity.fluidengine.ui.fluid.fluidDropInflation
import dev.antigravity.fluidengine.ui.fluid.fluidMenuLiquidContentMap
import dev.antigravity.fluidengine.ui.fluid.fluidMenuMaterialBlend
import dev.antigravity.fluidengine.ui.fluid.fluidMenuPanelLayout
import dev.antigravity.fluidengine.ui.fluid.fluidMenuPointerRelay
import dev.antigravity.fluidengine.ui.fluid.fluidPressable
import dev.antigravity.fluidengine.ui.fluid.fluidPressedBounds
import dev.antigravity.fluidengine.ui.fluid.glassControlSurface
import dev.antigravity.fluidengine.ui.fluid.rememberFluidMenuPanelMetrics
import dev.antigravity.fluidengine.ui.fluid.rememberFluidPressState
import dev.antigravity.fluidengine.ui.fluid.rememberGlassBackdrop
import dev.antigravity.fluidengine.ui.fluid.solveFluidMenuPanel
import dev.antigravity.fluidengine.ui.fluid.unclippedBoundsInRoot
import dev.antigravity.fluidengine.ui.haptics.FluidHapticEvent
import dev.antigravity.fluidengine.ui.haptics.FluidHaptics
import dev.antigravity.fluidengine.ui.haptics.LocalFluidHaptics

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
  internal var panelFrame by mutableStateOf<Rect?>(null)
  internal var panelLayout by mutableStateOf<FluidMenuPanelLayout?>(null)

  /** Vero dal gesto d'apertura alla richiesta di congedo. */
  var isOpen by mutableStateOf(false)
    internal set

  /** Vero finché c'è qualcosa in scena, viaggio di ritorno compreso: l'ancora resta nascosta. */
  var isOnScreen by mutableStateOf(false)
    internal set

  internal val physics = FluidPhysicsState(FluidForm.circle(Offset(1f, 1f), 1f))
  internal val journey = FluidMenuJourney()
  internal val selection = FluidMenuSelection()

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

  /** L'ancora del vecchio [open] pubblico, che un'ancora sua non ce l'ha. */
  private val publicAnchor = FluidMenuAnchor()

  /** Chiede il viaggio di ritorno. Il tasto riappare quando la superficie è tornata capsula. */
  fun dismiss() {
    if (!isOpen) return
    isOpen = false
    // Un congedo a dito giu' (il back durante il trascinamento): il dito non e' piu' del menu.
    anchor?.handOff?.sink = null
    selection.cancel()
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
    val m = metrics ?: return false
    val origin = hostOrigin ?: return false
    val size = hostSize ?: return false
    val layout = fluidMenuPanelLayout(items.map { it.startsSection }, m)
    val anchorLocal = anchor.restBounds.translate(-origin.x, -origin.y)
    val panel = solveFluidMenuPanel(
      anchor = anchorLocal,
      host = size,
      width = m.widthPx,
      height = layout.height,
      gapPx = m.gapPx,
      marginPx = m.marginPx,
      placement = FluidMenuPlacement.AboveOrBelow,
    )
    val reopening = isOnScreen
    actions = items
    panelLayout = layout
    panelFrame = panel
    this.anchor = anchor
    journey.prepare(
      anchor = anchor,
      hostOrigin = origin,
      panel = panel,
      radius = m.radiusPx,
      inflation = fluidDropInflation(panel.width, size.width, m.edgeMinPx),
      freshStart = !reopening,
    )
    journey.install(physics)
    selection.cancel()
    selection.geometry = fluidMenuGeometryOf(layout, items.map { it.enabled }, panel.translate(origin.x, origin.y))
    anchor.hidden = true
    isOnScreen = true
    isOpen = true
    journey.retarget(1f)
    if (handOff) anchor.handOff.attach(selection)
    return true
  }

  /** Una scelta: un colpo solo (Tap), il congedo, l'azione. */
  internal fun pick(index: Int, haptics: FluidHaptics) {
    if (!isOpen) return
    val action = actions.getOrNull(index) ?: return
    if (!action.enabled) return
    haptics.play(FluidHapticEvent.Tap)
    dismiss()
    action.onClick()
  }

  /** Il ritorno e' finito: il tasto torna a disegnarsi nello stesso istante in cui l'host smonta. */
  internal fun finishClose() {
    if (isOpen) return
    anchor?.hidden = false
    anchor = null
    panelFrame = null
    panelLayout = null
    isOnScreen = false
    journey.clearAnchor()
  }
}

@Composable
fun rememberFluidMorphMenuState(): FluidMorphMenuState = remember { FluidMorphMenuState() }

/**
 * L'ancora: una capsula di vetro coi gesti dei tasti — tocco per l'azione, tenuto premuto diventa il
 * menù (un `Open`, uno solo, e il dito passa al menu). Mentre il menù è in scena l'ancora non disegna
 * niente: al suo posto, allo stesso pixel, sta la superficie dell'host — la stessa capsula, che parte
 * per il viaggio.
 *
 * @param opensOnTap il tocco apre il menu invece di fare [onClick] (che allora non viene chiamato):
 *   per un tasto il cui unico mestiere e' il menu ("Ordina", "Altro"). Il tenuto premuto apre in
 *   ogni caso. Dalla 3.0.
 */
@Composable
fun FluidMorphMenuButton(
  state: FluidMorphMenuState,
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

  SideEffect {
    anchor.face = { MorphMenuAnchorLabel(text = text, icon = icon, color = contentColor) }
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
      .glassControlSurface(backdrop = backdrop, pressState = press)
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
      // Stessa altezza e stesso passo orizzontale di FluidGlassButton: l'ancora È quel tasto,
      // con un mestiere in più.
      .height(44.dp)
      .padding(horizontal = 20.dp),
    horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally),
    verticalAlignment = Alignment.CenterVertically,
  ) {
    MorphMenuAnchorLabel(text = text, icon = icon, color = contentColor)
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
  val reducedMotion = LocalFluidMotionPolicy.current.reducedMotion
  val density = LocalDensity.current
  val haptics = LocalFluidHaptics.current
  val touchSlop = LocalViewConfiguration.current.touchSlop
  val metrics = rememberFluidMenuPanelMetrics()
  state.physics.reducedMotion = reducedMotion
  state.physics.density = density.density

  SideEffect {
    state.metrics = metrics
    state.selection.touchSlop = touchSlop
    state.selection.onTick = { haptics.play(FluidHapticEvent.Tick) }
    state.selection.onPick = { index -> state.pick(index, haptics) }
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

  BackHandler(enabled = state.isOpen) { state.dismiss() }

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

  Box(
    modifier = modifier
      .fillMaxSize()
      .onGloballyPositioned {
        state.hostOrigin = it.localToRoot(Offset.Zero)
        state.hostSize = it.size.toSize()
      },
  ) {
    val anchor = state.anchor
    val panel = state.panelFrame
    val layout = state.panelLayout
    val origin = state.hostOrigin ?: Offset.Zero
    if (state.isOnScreen && anchor != null && panel != null && layout != null) {
      val progress: () -> Float = { state.journey.progress.value }

      // L'acchiappa-tocco: la pagina resta a colori — è il bordo del vetro a separare, non un velo.
      if (state.isOpen) {
        Box(
          modifier = Modifier
            .fillMaxSize()
            .pointerInput(state) { detectTapGestures(onTap = { state.dismiss() }) },
        )
      }

      Box(
        modifier = Modifier
          .fillMaxSize()
          .fluidPhysicsSurface(
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
          ),
      )

      // La faccia del tasto: parte col viaggio d'andata, seguendo la sagoma, e torna con quello di
      // ritorno. Spenta dal 35% del viaggio: le voci arrivano dal 25%, mai leggibili insieme.
      val anchorLocal = anchor.restBounds.translate(-origin.x, -origin.y)
      MorphMenuFrameBox(frame = anchorLocal) {
        Row(
          modifier = Modifier
            .fillMaxSize()
            .graphicsLayer {
              alpha = fluidMenuFaceAlpha(progress())
              val map = fluidMenuContentMap(target = anchorLocal, current = state.physics.contentBoundsNow())
              translationX = map.translation.x
              translationY = map.translation.y
              scaleX = map.scale
              scaleY = map.scale
            },
          horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally),
          verticalAlignment = Alignment.CenterVertically,
        ) {
          anchor.face?.invoke()
        }
      }

      // Le voci: arrivano attaccate alla superficie e, al ritorno, la seguono finche' si spengono.
      FluidGlassMenuPanel(
        actions = state.actions,
        selection = state.selection,
        layout = layout,
        panelMaterial = menuGlass,
        pageBackdrop = backdrop,
        contentAlpha = { fluidMenuContentAlpha(progress(), closing = state.journey.closing) },
        contentMap = {
          fluidMenuLiquidContentMap(
            target = panel,
            current = state.physics.contentBoundsNow(),
            alpha = fluidMenuContentAlpha(progress(), closing = state.journey.closing),
          )
        },
        onPick = { index -> state.pick(index, haptics) },
        modifier = Modifier.frame(panel),
        paneTitle = MorphMenuPaneTitle,
        // Le voci non escono mai dal vetro mentre si forma o si ritira: ritagliate sul corpo.
        travelClip = { state.physics.travelClipIn(panel, progress()) },
      )
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
    .offset { IntOffset(frame.left.toInt(), frame.top.toInt()) }
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

private const val MorphMenuPaneTitle = "Azioni"
