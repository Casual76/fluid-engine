/*
 * La macchina delle pagine (3.0, fase 8): due strati, un orologio, molle con la velocita' del dito.
 *
 * Il flusso avanti/indietro/predittivo e' portato da LibrePods, **GPL-3.0**:
 *
 *   me.kavishdevar.librepods.presentation.navigation.SwipeBack.kt
 *     righe 103-136 (la corsa in pixel: parte da W in avanti, da 0 indietro, e va a 0 o a W)
 *     righe 152-182 (il back predittivo: segue durante il gesto, conferma o annulla alla fine)
 *     righe 184-196 (il trascinamento accumula la corsa, mai sotto zero)
 *     righe 226-257 (il rilascio: conferma o torna)
 *   https://github.com/librepods-org/librepods, branch `android/rewrite`, commit 3d4d270.
 *
 * Licenza: GPL-3.0, https://www.gnu.org/licenses/gpl-3.0.html; vedi LICENSES/LibrePods.md.
 *
 * **Cosa abbiamo cambiato.**
 *   - il back di sistema segue `touchX` (il dito), non il `progress` di LibrePods (:156-162): la
 *     pagina sta sotto il dito anche col gesto di sistema; l'annullo si deduce in anticipo;
 *   - molle con la velocita' del dito invece dei `tween(150)`/`tween(220)` di durata fissa
 *     (:128-131, :165-177, :236-249): la velocita' del dito non va persa al rilascio;
 *   - un gesto confermato chiama `onBack()` *prima* della molla e la molla continua mentre la pila
 *     si accorcia: niente canale di richieste (:139-149), l'host fa il diff della pila;
 *   - push e pop a meta' finiscono subito e parte quello nuovo; un dito riprende un annullo a meta';
 *     durante una conferma la pagina che esce non prende tocchi;
 *   - la pagina sotto non si disegna mentre e' coperta, i passi fra schede restano quelli di prima.
 */
package dev.antigravity.fluidengine.ui.page

import android.view.View
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.AnimationSpec
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.shape.AbsoluteRoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.unit.IntSize
import androidx.navigationevent.NavigationEventHandler
import dev.antigravity.fluidengine.ui.fluid.FluidMotion
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

/**
 * Lo stato di un [FluidPageHost]: cosa si sta muovendo, quanto, e se c'e' un dito.
 *
 * Si legge (le shell dell'app, la tab bar che cambia visibilita' fra due pagine) e non si scrive: lo
 * pilotano la pila che l'host riceve, il dito e il back di sistema. Leggerne [progress] in
 * composizione ricompone a ogni fotogramma del movimento: va letto dove serve, in disegno se si puo'.
 */
@Stable
class FluidPageHostState internal constructor() {

  /** Che movimento c'e' adesso. */
  var kind: FluidPageMove by mutableStateOf(FluidPageMove.None)
    private set

  /** Il dito tiene la pagina (in-app o col gesto di sistema). */
  var isInteractive: Boolean by mutableStateOf(false)
    private set

  /** La pagina che sta lasciando il davanti (la chiave della pila), o null a riposo. */
  var from: Any? by mutableStateOf<Any?>(null)
    private set

  /** La pagina che sta prendendo il davanti, o null a riposo. */
  var to: Any? by mutableStateOf<Any?>(null)
    private set

  /**
   * Quanto del movimento da [from] a [to] e' fatto: 0 all'inizio, 1 alla fine, 0 a riposo. Un back
   * annullato torna verso 0.
   */
  val progress: Float
    get() = when (kind) {
      FluidPageMove.None -> 0f
      FluidPageMove.Push -> 1f - upperExitFraction()
      FluidPageMove.Pop, FluidPageMove.Back -> upperExitFraction()
      FluidPageMove.Peer -> peerEased
    }

  /**
   * La velocita' della pagina sopra lungo l'uscita, in px/s (positiva = verso fuori), mentre una
   * molla la muove. Al primo fotogramma dopo il rilascio e' quella del dito.
   */
  val velocity: Float get() = velocityPx

  /** Se qualcosa si muove (un back annullato che torna compreso). */
  val isMoving: Boolean get() = kind != FluidPageMove.None

  // --- Quello che si disegna (letto in disegno, mai in composizione) --------------------------------

  internal var travelPx by mutableFloatStateOf(0f)
  internal var velocityPx by mutableFloatStateOf(0f)
  internal var peerTime by mutableFloatStateOf(0f)
  internal var peerEased by mutableFloatStateOf(0f)
  internal var peerDirection by mutableIntStateOf(1)
  internal var upper: Any? by mutableStateOf<Any?>(null)
  internal var lower: Any? by mutableStateOf<Any?>(null)
  internal var upperTransition by mutableStateOf(FluidPageTransition.Slide)
  internal var opening by mutableStateOf(false)
  /** +1: la pagina sopra esce verso destra. */
  internal var direction by mutableIntStateOf(1)
  internal var width by mutableFloatStateOf(0f)
  internal var cardShape: Shape by mutableStateOf<Shape>(RectangleShape)
  internal var expandOrigin by mutableStateOf(TransformOrigin.Center)
  internal var initialized by mutableStateOf(false)

  internal var height by mutableFloatStateOf(0f)

  /**
   * Dove finisce la riga dei controlli della barra ancorata, in px dall'alto dell'host; 0 senza
   * barra. Un gesto che parte li' sopra e' dei controlli della barra, non della pagina.
   */
  internal var barBottomPx = 0f

  internal var hostLeftInWindow = 0f
  internal var hostRightInWindow = 0f
  internal var windowWidth = 0f
  private var cardRadii = FluidPageCorners.Zero

  // --- Il cablaggio, rinfrescato a ogni composizione dell'host -------------------------------------

  internal var scope: CoroutineScope? = null
  internal var density = 1f
  internal var layoutDirectionSign = 1
  internal var reducedMotion = false
  internal var onBack: () -> Unit = {}
  internal var onTransitionStart: (List<Any>) -> Unit = {}
  internal var onSettled: () -> Unit = {}
  internal var transitionOf: (Any) -> FluidPageTransition = { FluidPageTransition.Slide }
  internal var backSwipeOf: (Any) -> FluidBackSwipe = { FluidBackSwipe.Anywhere }
  internal var peerOf: ((Any, Any) -> Int)? = null
  internal var renderable: (Any) -> Boolean = { true }
  internal var backHandler: NavigationEventHandler<*>? = null

  // --- La macchina ---------------------------------------------------------------------------------

  internal enum class Phase { Idle, Pushing, Popping, Peer, Dragging, Committing, Cancelling }

  internal var phase: Phase = Phase.Idle
    private set

  /** Cambia a ogni presa del dito: un riconoscitore che tiene un gettone vecchio ha perso la pagina. */
  private var dragToken = 0
  private var processed: List<Any> = emptyList()
  private var job: Job? = null

  /** Il riconoscitore segue un dito che un figlio si e' preso: uno scorrimento puo' passargli la mano. */
  internal var handoffWatcher = false
  private var pendingHandoff = 0f

  internal val stackSize: Int get() = processed.size

  internal fun upperExitFraction(): Float =
    FluidPageMotion.exitFraction(upperTransition, travelPx, width)

  /** La frazione con cui si decide chi e' davanti: per un passo fra schede, quella del passo. */
  internal fun frontFraction(): Float =
    if (kind == FluidPageMove.Peer) 1f - peerEased else upperExitFraction()

  internal fun exitTravel(): Float = FluidPageMotion.exitTravel(upperTransition, width)

  // --- Dove sta ogni pagina, letto in disegno ------------------------------------------------------

  /** Che parte fa una pagina adesso: la decide lo stato, non la composizione (che arriva un fotogramma dopo). */
  internal enum class PageRole { Upper, Lower, Gone }

  internal fun roleOf(page: Any): PageRole = when (page) {
    upper -> PageRole.Upper
    lower -> PageRole.Lower
    else -> PageRole.Gone
  }

  /** La traslazione orizzontale di una pagina in questo fotogramma, in px (gia' nel verso). */
  internal fun pageOffsetX(role: PageRole): Float = when (role) {
    PageRole.Gone -> width * direction
    PageRole.Upper -> if (kind == FluidPageMove.Peer) {
      FluidPageMotion.peerFrame(peerTime, peerEased, width, peerDirection).first
    } else {
      FluidPageMotion.pageFrame(upperTransition, travelPx, width, opening).translation * direction
    }
    PageRole.Lower -> when {
      kind == FluidPageMove.Peer -> FluidPageMotion.peerFrame(peerTime, peerEased, width, peerDirection).second
      opening && upperTransition == FluidPageTransition.Expand -> 0f
      else -> FluidPageMotion.belowParallax(upperExitFraction(), width) * direction
    }
  }

  /** La scala di una pagina in questo fotogramma, attorno al suo centro (o al punto toccato per Expand). */
  internal fun pageScale(role: PageRole): Float =
    if (role == PageRole.Upper && kind != FluidPageMove.Peer) {
      FluidPageMotion.pageFrame(upperTransition, travelPx, width, opening).scale
    } else {
      1f
    }

  /** Da dove a dove (x, px) la pagina sopra copre quella sotto in questo fotogramma. */
  internal fun upperCoverage(): Pair<Float, Float> {
    val x = pageOffsetX(PageRole.Upper)
    val inset = width * (1f - pageScale(PageRole.Upper)) / 2f
    return (x + inset) to (x + width - inset)
  }

  /** La pila che l'host ha appena ricevuto. Chiamata dopo ogni composizione dell'host. */
  internal fun onStack(stack: List<Any>) {
    if (stack == processed) return
    val old = processed
    processed = stack.toList()
    if (old.isEmpty() || width <= 0f || scope == null) {
      // La prima pila, o un host largo zero (un pannello fuori scena): niente da animare.
      finishNow()
      settle()
      return
    }
    val change = FluidPageMotion.classifyStackChange(old, processed, peerOf)
    when (change.kind) {
      FluidStackChangeKind.None -> if (phase == Phase.Idle) settle()
      FluidStackChangeKind.Pop -> beginPop(change.from!!, change.to!!)
      FluidStackChangeKind.Push, FluidStackChangeKind.Replace -> beginPush(change.from!!, change.to!!)
      FluidStackChangeKind.Peer -> beginPeer(change.from!!, change.to!!, change.direction)
    }
  }

  private fun beginPush(from: Any, to: Any) {
    finishNow()
    upperTransition = transitionOf(to)
    upper = to
    lower = from.takeIf(renderable)
    opening = true
    direction = layoutDirectionSign
    show(FluidPageMove.Push, from = from, to = to)
    travelPx = exitTravel()
    phase = Phase.Pushing
    animateTravel(0f, FluidPageMotion.page(), 0f) { settle() }
  }

  private fun beginPop(from: Any, to: Any) {
    if (upper == from && (phase == Phase.Committing || phase == Phase.Dragging || phase == Phase.Cancelling)) {
      // Il pop del gesto stesso (onBack l'ha appena chiesto), o la pagina tolta da altrove mentre
      // il dito la teneva: la molla continua da dove e', con la velocita' che ha.
      if (lower != to) lower = to.takeIf(renderable)
      if (phase != Phase.Committing) commit(velocityPx)
      return
    }
    finishNow()
    if (!renderable(from)) {
      // Una pagina gia' distrutta (tolta senza transizione) non si disegna: si va a riposo.
      settle()
      return
    }
    upperTransition = transitionOf(from)
    upper = from
    lower = to.takeIf(renderable)
    opening = false
    direction = layoutDirectionSign
    show(FluidPageMove.Pop, from = from, to = to)
    travelPx = 0f
    phase = Phase.Popping
    animateTravel(exitTravel(), FluidPageMotion.page(), 0f) { settle() }
  }

  private fun beginPeer(from: Any, to: Any, peer: Int) {
    finishNow()
    upperTransition = transitionOf(to)
    upper = to
    lower = from.takeIf(renderable)
    opening = false
    // La scheda che viene dopo arriva dal lato di fine: da destra in LTR, da sinistra in RTL.
    peerDirection = peer * layoutDirectionSign
    show(FluidPageMove.Peer, from = from, to = to)
    peerTime = 0f
    peerEased = 0f
    phase = Phase.Peer
    val scope = scope
    if (reducedMotion || scope == null) {
      settle()
      return
    }
    job = scope.launch {
      Animatable(0f).animateTo(1f, tween(FluidMotion.DurationPeer, easing = LinearEasing)) {
        peerTime = value
        peerEased = FluidMotion.EaseEmphasized.transform(value)
      }
      job = null
      settle()
    }
  }

  private fun show(move: FluidPageMove, from: Any?, to: Any?) {
    kind = move
    this.from = from
    this.to = to
  }

  /**
   * Il movimento in corso finisce adesso, al suo stato finale (o a riposo, per un dito), senza
   * dichiararlo fermo: ne parte subito un altro, e le voci che il primo stava portando via devono
   * restare vive finche' il secondo non arriva a riposo (una pagina distrutta non si disegna).
   */
  private fun finishNow() {
    if (phase == Phase.Idle) return
    if (phase == Phase.Dragging) dragToken++
    job?.cancel()
    job = null
    settle(notify = false)
  }

  private fun settle(notify: Boolean = true) {
    val top = processed.lastOrNull()
    phase = Phase.Idle
    show(FluidPageMove.None, from = null, to = null)
    isInteractive = false
    travelPx = 0f
    velocityPx = 0f
    peerTime = 0f
    peerEased = 0f
    opening = false
    upper = top
    lower = processed.getOrNull(processed.size - 2)
    upperTransition = top?.let(transitionOf) ?: FluidPageTransition.Slide
    direction = layoutDirectionSign
    initialized = true
    if (notify) onSettled()
  }

  private fun animateTravel(
    target: Float,
    spec: AnimationSpec<Float>,
    initialVelocity: Float,
    onEnd: () -> Unit,
  ) {
    job?.cancel()
    job = null
    val scope = scope
    if (reducedMotion || scope == null) {
      // Animazioni ridotte: i moti autonomi saltano alla fine. Il dito, prima, ha seguito 1:1.
      travelPx = target
      velocityPx = 0f
      onEnd()
      return
    }
    val animatable = Animatable(travelPx)
    velocityPx = initialVelocity
    job = scope.launch {
      animatable.animateTo(target, spec, initialVelocity) {
        travelPx = value
        velocityPx = velocity
      }
      job = null
      velocityPx = 0f
      onEnd()
    }
  }

  // --- Il dito -------------------------------------------------------------------------------------

  /** Se adesso un gesto indietro puo' cominciare (o riprendere una pagina che torna a posto). */
  internal fun canStartGesture(): Boolean {
    if (width <= 0f || processed.size < 2) return false
    val top = processed.last()
    val below = processed[processed.size - 2]
    // Una scheda sopra una scheda: non sono una dentro l'altra, il dito non le separa.
    if ((peerOf?.invoke(below, top) ?: 0) != 0) return false
    if (!renderable(below)) return false
    return when (phase) {
      Phase.Idle, Phase.Pushing, Phase.Peer -> true
      Phase.Cancelling -> upper == top
      else -> false
    }
  }

  /** Da dove si puo' tirare la cima, o null se adesso non si puo'. */
  internal fun swipeMode(): FluidBackSwipe? =
    if (canStartGesture()) backSwipeOf(processed.last()) else null

  /** Un dito che si posa su una pagina che sta tornando a posto la riprende. */
  internal fun canGrab(): Boolean =
    phase == Phase.Cancelling && canStartGesture() && backSwipeOf(processed.last()) != FluidBackSwipe.Off

  /**
   * Comincia (o riprende) un gesto indietro. Torna il gettone del gesto, o null se non si puo'.
   * [direction]: +1 se la pagina esce verso destra.
   */
  internal fun beginDrag(direction: Int): Int? {
    if (!canStartGesture()) return null
    if (phase == Phase.Cancelling) {
      // Ripresa al volo: corsa, verso e pagine restano quelli di prima.
      job?.cancel()
      job = null
    } else {
      finishNow()
      val top = processed.last()
      val below = processed[processed.size - 2]
      upperTransition = transitionOf(top)
      upper = top
      lower = below
      opening = false
      travelPx = 0f
      this.direction = direction
      onTransitionStart(listOf(top, below))
    }
    phase = Phase.Dragging
    show(FluidPageMove.Back, from = upper, to = lower)
    isInteractive = true
    velocityPx = 0f
    return ++dragToken
  }

  internal fun isDragging(token: Int): Boolean = phase == Phase.Dragging && dragToken == token

  /** Snap sincrono: il dito non aspetta nessuna molla. */
  internal fun dragTo(token: Int, travel: Float) {
    if (!isDragging(token)) return
    travelPx = travel.coerceIn(0f, exitTravel())
  }

  /**
   * Il dito si e' alzato. [velocity] in px/s lungo l'uscita; [decided] e' la decisione del sistema
   * (il back di sistema conferma o annulla da se'), null per la regola del rilascio in-app.
   */
  internal fun release(token: Int, velocity: Float, decided: Boolean? = null) {
    if (!isDragging(token)) return
    isInteractive = false
    val commit = decided ?: FluidPageMotion.backRelease(travelPx, velocity, width, density)
    if (commit) commit(velocity) else cancelGesture(velocity)
  }

  /** Il sistema ha confermato un back che avevamo gia' dato per annullato: si va fuori da qui. */
  internal fun systemCommit(token: Int, velocity: Float) {
    if (dragToken != token) return
    if (phase == Phase.Dragging || phase == Phase.Cancelling) {
      isInteractive = false
      commit(velocity)
    }
  }

  private fun commit(velocity: Float) {
    phase = Phase.Committing
    show(FluidPageMove.Back, from = upper, to = lower)
    isInteractive = false
    val departing = upper
    // Prima onBack, poi la molla: la pila si accorcia mentre la pagina vola, e [beginPop] riconosce
    // il pop come il proprio invece di ricominciare.
    if (departing != null && departing in processed) onBack()
    animateTravel(exitTravel(), FluidPageMotion.page(), velocity) { finishCommit(departing) }
  }

  /**
   * La molla e' arrivata fuori. Se la pila non si e' ancora accorciata (con le animazioni ridotte la
   * molla finisce prima della composizione che porta il pop) si aspettano due fotogrammi; se dopo
   * la pagina e' ancora li', l'app non l'ha tolta (un "salvare le modifiche?") e torna dov'era.
   */
  private fun finishCommit(departing: Any?) {
    if (departing == null || departing !in processed) {
      settle()
      return
    }
    val scope = scope ?: run {
      settle()
      return
    }
    job = scope.launch {
      repeat(2) { withFrameNanos { } }
      job = null
      if (departing in processed) {
        phase = Phase.Cancelling
        animateTravel(0f, FluidPageMotion.pageReturn(), 0f) { settle() }
      } else {
        settle()
      }
    }
  }

  private fun cancelGesture(velocity: Float) {
    phase = Phase.Cancelling
    animateTravel(0f, FluidPageMotion.pageReturn(), velocity) { settle() }
  }

  /** Back senza gesto (3 tasti, Android 13 e prima, tastiera): l'app toglie, la molla accompagna. */
  internal fun requestBack() {
    if (processed.size > 1) onBack()
  }

  /** Durante una conferma la pagina che esce non prende tocchi; durante un push, quella coperta. */
  internal fun blocksInput(page: Any): Boolean = when (phase) {
    Phase.Committing, Phase.Popping -> page == upper
    Phase.Pushing, Phase.Peer -> page == lower
    else -> false
  }

  /** Uno scorrimento orizzontale arrivato al suo inizio offre il resto del dito alla pagina. */
  internal fun offerHandoff(amount: Float): Boolean {
    if (!handoffWatcher || swipeMode() != FluidBackSwipe.Anywhere) return false
    pendingHandoff += amount
    return true
  }

  internal fun takeHandoff(): Float {
    val amount = pendingHandoff
    pendingHandoff = 0f
    return amount
  }

  // --- Geometria -----------------------------------------------------------------------------------

  internal fun recordDown(position: Offset, size: IntSize) {
    if (size.width <= 0 || size.height <= 0) return
    expandOrigin = TransformOrigin(
      (position.x / size.width).coerceIn(0f, 1f),
      (position.y / size.height).coerceIn(0f, 1f),
    )
  }

  internal fun updateGeometry(coordinates: LayoutCoordinates, view: View) {
    width = coordinates.size.width.toFloat()
    height = coordinates.size.height.toFloat()
    val bounds = coordinates.boundsInWindow()
    hostLeftInWindow = bounds.left
    hostRightInWindow = bounds.right
    val root = view.rootView
    windowWidth = root.width.toFloat()
    val radii = FluidPageMotion.pageCardRadii(
      hostLeft = bounds.left,
      hostTop = bounds.top,
      hostRight = bounds.right,
      hostBottom = bounds.bottom,
      windowWidth = root.width.toFloat(),
      windowHeight = root.height.toFloat(),
      display = displayCornerRadii(view),
    )
    if (radii != cardRadii) {
      cardRadii = radii
      // Raggio costante per tutto il movimento: la forma si crea qui, non a ogni fotogramma, e il
      // layer non ritaglia di nuovo. Circolare di proposito: e' il rettangolo arrotondato hardware
      // del display, ed e' il ritaglio che il layer fa gratis.
      cardShape = if (radii.isZero) {
        RectangleShape
      } else {
        AbsoluteRoundedCornerShape(
          topLeft = radii.topLeft,
          topRight = radii.topRight,
          bottomRight = radii.bottomRight,
          bottomLeft = radii.bottomLeft,
        )
      }
    }
  }
}

/** Uno stato per host, ricordato. */
@Composable
fun rememberFluidPageHostState(): FluidPageHostState = remember { FluidPageHostState() }
