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
 *   - sulla stessa pagina Slide e ShrinkSlide invertono push e pop senza perdere corsa e velocita';
 *     Expand finisce prima l'apertura; un dito riprende anche un annullo a meta';
 *     durante una conferma la pagina che esce non prende tocchi;
 *   - la pagina sotto non si disegna mentre e' coperta, i passi fra schede restano quelli di prima;
 *   - (4.0, F5B.3) Expand con una sorgente dichiarata e' uno zoom: la sagoma viaggia sullo stesso orologio della
 *     corsa, ripartendo da quella che si vede a ogni cambio di fase (push, pop, dito, conferma, annullo).
 */
package dev.antigravity.fluidengine.ui.page

import android.view.View
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.AnimationSpec
import androidx.compose.animation.core.AnimationState
import androidx.compose.animation.core.animateTo
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import dev.antigravity.fluidengine.ui.fluid.glassContentSmoothstep
import androidx.compose.ui.MotionDurationScale
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
import androidx.compose.runtime.Immutable
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.input.pointer.PointerInputChange
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.LayoutDirection
import androidx.navigationevent.NavigationEventHandler
import dev.antigravity.fluidengine.ui.fluid.FluidMotion
import dev.antigravity.fluidengine.ui.fluid.FluidTransitionSnapshot
import dev.antigravity.fluidengine.ui.fluid.FluidTransitionSource
import dev.antigravity.fluidengine.ui.fluid.FluidTransitionSourceGeometry
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlin.math.abs

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

  /**
   * Quanto si e' andati verso le pagine per cui [isHidden] e' vero (3.0.1): 0 su una pagina dove una cosa
   * c'e', 1 su una dove non c'e', e in mezzo mentre le pagine si muovono, al passo di [progress] — un back
   * a dito compreso, che puo' tornare indietro.
   *
   * E' il conto della tab bar che se ne va con la pagina di dettaglio: due app lo rifacevano da [from], [to]
   * e [progress], e ognuna sbagliava un caso (il riposo, il back annullato, due pagine che la nascondono
   * tutte e due). [isHidden] riceve la chiave della pagina — in `FluidNavHost` la `NavBackStackEntry` — o
   * null prima della prima pila:
   *
   * ```kotlin
   * val hidden: (Any?) -> Boolean = { (it as? NavBackStackEntry)?.destination?.route == "dettaglio" }
   * Modifier.graphicsLayer { translationY = pages.fractionToward(hidden) * size.height }
   * ```
   *
   * Legge lo stato del movimento: chiamata in composizione ricompone a ogni fotogramma, quindi va letta in
   * disegno o in layout (un `graphicsLayer`, un `offset { }`), come [progress].
   */
  fun fractionToward(isHidden: (Any?) -> Boolean): Float {
    if (!isMoving) return if (isHidden(upper)) 1f else 0f
    val start = if (isHidden(from)) 1f else 0f
    val end = if (isHidden(to)) 1f else 0f
    if (start == end) return start
    return start + (end - start) * progress.coerceIn(0f, 1f)
  }

  // --- Quello che si disegna (letto in disegno, mai in composizione) --------------------------------

  private var currentTravel by mutableFloatStateOf(0f)
  internal var travelPx: Float
    get() = currentTravel
    set(value) {
      if (landing > 0f && value > currentTravel && exitTravel() > 0f) {
        val detach = (value - currentTravel) / exitTravel()
        landing = (landing - detach / (1f - FluidPageMotion.GrowMeltStart)).coerceAtLeast(0f)
      }
      currentTravel = value
    }

  /** Tempo di posa già percorso. Il dito può disfarlo, anche se riprende la pagina a metà posa. */
  private var landing by mutableFloatStateOf(0f)
  internal val solidOpacity: Float get() = if (growing != null) FastOutSlowInEasing.transform(landing) else 0f
  internal var growing by mutableStateOf<GrowingPageGlass?>(null)
    private set
  internal val sharedPair by androidx.compose.runtime.derivedStateOf { findSharedPair() }
  internal var sharedOrigins by mutableStateOf(emptyList<SharedOrigin>())
    private set
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
  /** I raggi della carta (gli angoli del telefono dove l'host li tocca): scritti da [updateGeometry]; le tracce li danno a mano. */
  internal var cardRadii = FluidPageCorners.Zero

  /** Dove sta l'host nella radice Compose: le sorgenti dello zoom danno il loro riquadro lì. */
  internal var hostOriginInRoot = Offset.Zero

  /**
   * Le coordinate dell'host, quelle della sagoma dello zoom ([zoomShape]). Chi disegna in un riquadro suo, come il titolo
   * della barra ancorata che sta sotto la barra di stato, ci riporta la sagoma (corr-s4-zoom).
   */
  internal var hostCoordinates: LayoutCoordinates? = null

  // --- Lo zoom dalla sorgente (4.0, F5B.3) ------------------------------------------------------------------------

  /**
   * Il viaggio della sagoma del movimento in corso, o null: senza sorgente, con `Slide` e `ShrinkSlide`, con le
   * animazioni ridotte e quando la sorgente non c'è e vale il ripiego (l'`Expand` di sempre). Uno stato solo, sostituito
   * a ogni cambio di fase: il disegno lo rilegge e la sagoma riparte da quella che si vede.
   */
  internal var zoom: ZoomJourney? by mutableStateOf<ZoomJourney?>(null)
    private set

  /** Sorgenti preparate all'apertura e nascoste dal primo disegno del fronte fino all'uscita della pagina. */
  private val zoomSources = HashMap<Any, FluidTransitionSource>()

  // --- Il cablaggio, rinfrescato a ogni composizione dell'host -------------------------------------

  internal var scope: CoroutineScope? = null
  internal var density = 1f
  internal var layoutDirectionSign = 1
  internal var reducedMotion = false
  internal var onBack: () -> Unit = {}
  internal var onTransitionStart: (List<Any>) -> Unit = {}
  internal var onSettled: () -> Unit = {}
  internal var transitionOf: (Any) -> FluidPageTransition = { FluidPageTransition.Slide }
  internal var backSwipeOf: (Any) -> FluidBackSwipe = { FluidBackSwipe.EdgeOnly }
  internal var dismissOf: (Any) -> FluidPageDismiss = { FluidPageDismiss.None }
  internal var peerOf: ((Any, Any) -> Int)? = null
  internal var renderable: (Any) -> Boolean = { true }
  internal var sourceOf: (Any) -> FluidTransitionSource? = { null }

  /** Dove si legge una sorgente: la sua geometria di adesso, anche nella pagina coperta. Sostituibile nei test. */
  internal var resolveSource: (FluidTransitionSource) -> FluidTransitionSourceGeometry? =
    { it.resolve(includeCoveredPage = true) }
  internal var backHandler: NavigationEventHandler<*>? = null

  /**
   * Manda al dispatcher del back un "indietro" completato, quello che il sistema manda a fine gesto
   * e il tasto di navigazione manda subito: lo risponde il gestore abilitato registrato per ultimo.
   * Se e' quello dell'host ([FluidPageBackHandler.onBackCompleted], poi [requestBack]) la pagina esce;
   * se e' di qualcun altro — il `BackHandler` della pagina, un modale, una ricerca aperta — risponde
   * lui, e l'host non fa niente. Null fuori da un dispatcher (l'host non e' dentro un'attivita').
   */
  internal var dispatchBack: (() -> Unit)? = null

  /**
   * La velocita' (px/s) del dito in-app che ha confermato un back e aspetta che il dispatcher scelga
   * chi lo risponde, o null. Lo prende [requestBack] se risponde l'host; se resta, ha risposto altri.
   */
  private var awaitingAnswer: Float? = null

  // --- La macchina ---------------------------------------------------------------------------------

  internal enum class Phase { Idle, Pushing, Popping, Peer, Dragging, Committing, Cancelling, VerticalSettling }

  internal var phase: Phase = Phase.Idle
    private set

  /** Cambia a ogni presa del dito: un riconoscitore che tiene un gettone vecchio ha perso la pagina. */
  private var dragToken = 0
  private var processed by mutableStateOf<List<Any>>(emptyList())
  private var job: Job? = null
  private var touchClock: Job? = null
  private var touchMoved = false
  private var lastTravelFrame: Long? = null

  // Il push è una richiesta alla pila dell'app. Fra la richiesta e la composizione che la
  // riceve conserviamo anche un eventuale rilascio: il primo frame legge già il dito giusto.
  private class OpenRequest(val from: Any, val token: Int) {
    var exit = 1f
    var velocity = 0f
    var released = false
    var cancelled = false
  }
  private var pendingOpen: OpenRequest? = null
  private var vertical by mutableStateOf(false)
  private var fingerOpens = false
  private var settlingOpen: Boolean? = null
  internal var restoreAfterRotation: VerticalPageRotation? = null
  // Alla ripresa la molla conserva la propria velocità, poi il suo scarto dal dito torna a zero.
  // Offset e velocità sono frazioni dell'altezza: rimangono validi anche durante un ridimensionamento.
  private var catchOffset = 0f
  private var catchVelocity = 0f
  private var sourceNeedsPlacement = false
  private var openContact: PointerInputChange? = null
  private var observedContact: PointerInputChange? = null
  private var openAction: (() -> Unit)? = null

  internal fun observeContact(down: PointerInputChange) { observedContact = down }

  internal fun observesContact(down: PointerInputChange): Boolean =
    observedContact?.let { it.id == down.id && it.uptimeMillis == down.uptimeMillis } == true

  internal fun markOpenContact(down: PointerInputChange, action: () -> Unit) {
    openContact = down
    openAction = action
  }

  internal fun takeOpenContact(down: PointerInputChange): (() -> Unit)? {
    observedContact = null
    val hit = openContact
    val action = openAction.takeIf { hit?.id == down.id && hit.uptimeMillis == down.uptimeMillis }
    openContact = null
    openAction = null
    return action
  }
  internal val canRetakeVertical: Boolean get() =
    vertical && phase == Phase.VerticalSettling && upper == interactionPage
  /** A composed destination which paints its own backdrop can reveal the page underneath. */
  internal val ownPageBackground = androidx.compose.runtime.mutableStateMapOf<Any, androidx.compose.ui.graphics.Color>()
  private var closingBackdropCeiling = 1f

  internal fun hasClosingBackdrop(page: Any): Boolean = upper == page && kind != FluidPageMove.None && !opening

  /** Only the final 12% of the opening exposes the backdrop (Alessio, visual estimate 2026-11-22). */
  internal fun backdropArrival(page: Any): Float {
    if (upper != page || kind == FluidPageMove.None) return 1f
    val exit = upperExitFraction()
    if (opening) return glassContentSmoothstep(FluidZoom.ViewerBackdropStart, 1f, 1f - exit)
    val end = if (vertical || kind == FluidPageMove.Back) FluidZoom.ViewerGestureFadeEnd else FluidZoom.ViewerBackFadeEnd
    return minOf(closingBackdropCeiling, 1f - glassContentSmoothstep(0f, end, exit))
  }

  /** An optional distance/velocity rule belonging to one composed destination. Coordinates are host pixels. */
  internal val verticalDismissRelease = mutableMapOf<Any, (Float, Float) -> Boolean>()
  internal val canDismissDown: Boolean get() =
    canRetakeVertical || (canStartGesture() && interactionPage?.let(dismissOf) == FluidPageDismiss.Down)
  /**
   * Identifica la pagina ancora invisibile per un pop nello stesso frame (C7). Solo la scheda e la dissolvenza
   * aspettano il posizionamento. La molla del push ordinario parte al cambio di pila.
   */
  internal var pendingPlacement: Any? by mutableStateOf(null)
    private set

  /** Il riconoscitore segue un dito che un figlio si e' preso: uno scorrimento puo' passargli la mano. */
  internal var handoffWatcher = false
  private var pendingHandoff = 0f

  internal val stackSize: Int get() = processed.size

  /** Un'aggiunta in coda da fermo può condividere la composizione che ha ricevuto la nuova pila. */
  internal fun canComposePush(stack: List<Any>): Boolean =
    initialized && !isMoving && width > 0f && processed.isNotEmpty() && stack.size == processed.size + 1 &&
      stack.last() !in processed && processed.indices.all { processed[it] == stack[it] }

  /** I controlli seguono subito la pila richiesta, anche mentre la pagina uscente resta disegnata. */
  internal val interactionPage: Any? get() = processed.lastOrNull()

  internal fun upperExitFraction(): Float = if (vertical && height > 0f) {
    (travelPx / height).coerceIn(0f, 1f)
  } else FluidPageMotion.exitFraction(upperTransition, travelPx, width)

  /** La frazione con cui si decide chi e' davanti: per un passo fra schede, quella del passo. */
  internal fun frontFraction(): Float =
    if (kind == FluidPageMove.Peer) 1f - peerEased else upperExitFraction()

  internal val crossfading: Boolean
    get() = reducedMotion && (vertical || kind == FluidPageMove.Push || kind == FluidPageMove.Pop || kind == FluidPageMove.Peer)

  internal fun exitTravel(): Float = if (vertical) height else FluidPageMotion.exitTravel(upperTransition, width)

  // --- Dove sta ogni pagina, letto in disegno ------------------------------------------------------

  /** Che parte fa una pagina adesso: la decide lo stato, non la composizione (che arriva un fotogramma dopo). */
  internal enum class PageRole { Upper, Lower, Gone }

  internal fun roleOf(page: Any): PageRole = when (page) {
    upper -> PageRole.Upper
    lower -> PageRole.Lower
    else -> PageRole.Gone
  }

  /** Letto nel disegno della singola pagina. Una pagina promossa a cima perde il velo
   * nello stesso fotogramma. Il visore dispone di un proprio fondo, anche in chiusura. */
  internal fun dimOn(page: Any): Float {
    if (page != lower || !isMoving || kind == FluidPageMove.Peer || reducedMotion || upper in ownPageBackground) return 0f
    val arrival = 1f - upperExitFraction()
    val journey = zoom ?: return FluidPageMotion.SlideDim * arrival
    val bounds = zoomShape()?.bounds ?: return 0f
    val source = journey.source.bounds
    // La corsa del dito può traslare la carta molto più di quanto la contragga.
    // Misuriamo il lato che varia di più in proporzione all'host: resta lo stesso
    // anche quando una rotazione cambia il rapporto fra altezza e larghezza.
    val dw = width - source.width
    val dh = height - source.height
    val growth = when {
      dh != 0f && abs(dh) * width >= abs(dw) * height -> (bounds.height - source.height) / dh
      dw != 0f -> (bounds.width - source.width) / dw
      else -> arrival
    }
    return FluidPageMotion.zoomDim(growth, journey.returnDim)
  }

  /** La traslazione orizzontale di una pagina in questo fotogramma, in px (gia' nel verso). */
  internal fun pageOffsetX(role: PageRole): Float = if (crossfading || (vertical && zoom == null)) 0f else when (role) {
    PageRole.Gone -> width * direction
    PageRole.Upper -> if (kind == FluidPageMove.Peer) {
      FluidPageMotion.peerFrame(peerTime, peerEased, width, peerDirection).first
    } else {
      zoomContent()?.translation?.x
        ?: (FluidPageMotion.pageFrame(upperTransition, travelPx, width, opening).translation * direction)
    }
    PageRole.Lower -> when {
      kind == FluidPageMove.Peer -> FluidPageMotion.peerFrame(peerTime, peerEased, width, peerDirection).second
      else -> FluidPageMotion.belowOffset(upperTransition, upperExitFraction(), width) * direction
    }
  }

  /** Tiene intere le prime lettere dell'intestazione che entra dal bordo di lettura. La carta conserva il suo passo. */
  internal fun headingCompensation(page: Any): Float {
    val followBack = kind == FluidPageMove.Back && FluidPageBarDiagnostics.gestureFollowsFinger
    if (page != to || (kind != FluidPageMove.Peer && !followBack)) return 0f
    val offset = pageOffsetX(roleOf(page))
    return if (offset * layoutDirectionSign < 0f) -offset else 0f
  }

  /** La scala di una pagina in questo fotogramma, attorno al suo centro (o al punto toccato per Expand). */
  internal fun pageScale(role: PageRole): Float =
    if (!crossfading && !(vertical && zoom == null) && role == PageRole.Upper && kind != FluidPageMove.Peer) {
      zoomContent()?.scale ?: FluidPageMotion.pageFrame(upperTransition, travelPx, width, opening).scale
    } else {
      1f
    }

  /**
   * La traslazione verticale di una pagina in questo fotogramma, in px: zero sempre, tranne la pagina sopra durante uno
   * zoom, che sta dentro la sua sagoma (scala attorno al centro dell'host, poi questo spostamento e [pageOffsetX]).
   */
  internal fun pageOffsetY(role: PageRole): Float =
    if (!crossfading && role == PageRole.Upper && kind != FluidPageMove.Peer)
      zoomContent()?.translation?.y ?: if (vertical && !reducedMotion) travelPx else 0f
    else 0f

  /** Il riquadro di riposo di una pagina con la sua forma da carta: la sagoma d'arrivo di uno zoom. */
  internal fun fullZoomShape(): FluidZoomShape = FluidZoomShape(Rect(0f, 0f, width, height), cardRadii)

  /**
   * La sagoma della pagina sopra in questo fotogramma, o null senza zoom. Mentre il dito la tiene la segue
   * ([FluidZoom.follow]); altrimenti va dalla sagoma da cui la fase e' partita a quella d'arrivo, sulla corsa.
   */
  internal fun zoomShape(): FluidZoomShape? {
    val journey = zoom ?: return null
    if (crossfading || kind == FluidPageMove.Peer || kind == FluidPageMove.None) return null
    val full = fullZoomShape()
    val shape = if (vertical) {
      val exit = upperExitFraction()
      val via = journey.viaExit
      when {
        via == null -> FluidZoom.lerp(full, journey.source, exit)
        exit <= via -> FluidZoom.lerp(full, journey.from, exit / via)
        else -> FluidZoom.lerp(journey.from, journey.source, (exit - via) / (1f - via))
      }
    } else if (journey.following) {
      FluidZoom.follow(journey.from, full, travelPx - journey.fromTravel, journey.fromTravel, direction, journey.pivot, width)
    } else {
      FluidZoom.lerp(journey.from, journey.to ?: full, FluidZoom.travelFraction(travelPx, journey.fromTravel, journey.toTravel))
    }
    val cornerStart = if (growing != null) FluidPageMotion.GrowCornerStart else 0f
    val docked = glassContentSmoothstep(cornerStart, 1f, landing)
    return shape.copy(corners = FluidZoom.pageCorners(shape.bounds, journey.source, full, density, docked))
  }

  /** Dove sta la pagina sopra dentro la sua sagoma: vedi [FluidZoom.content]. */
  internal fun zoomContent(): FluidZoomContent? = zoomShape()?.let { FluidZoom.content(it, Rect(0f, 0f, width, height)) }

  /**
   * Vero mentre lo zoom tiene la pagina sopra sotto la riga dei controlli della barra: lì sotto c'è ancora la pagina
   * sotto, e il vetro della barra rifrange quella (la registrazione della pagina sopra è intera e non scalata).
   */
  internal fun zoomBelowBar(): Boolean = zoomShape()?.let { it.bounds.top > barBottomPx + 0.5f } ?: false

  /**
   * La dissolvenza di Apple, **di serie** (scelta di Alessio del 19 novembre 2026, sui video a tempo vero di corr-zoom):
   * dentro la sagoma la pagina si dissolve nella sorgente e la sorgente nella pagina, da circa un terzo della strada
   * ([FluidZoom.crossfade]). Si legge dalla strada, quindi vale nei due versi: all'apertura la card sbiadisce mentre la
   * pagina compare, al ritorno (col tasto o col dito) la card compare sopra la pagina che sbiadisce. La sorgente consumata
   * si registra in un layer ([FluidTransitionSource.snapshot]) e l'host la disegna dentro la sagoma. Con le animazioni
   * ridotte non c'è zoom e quindi nemmeno dissolvenza: resta l'`Expand` di sempre. Spenta (le tracce di confronto), la
   * card rinasce solo quando la sagoma è arrivata, com'era fino a F5B.3.
   */
  internal var zoomCrossfade: Boolean = true

  /** Quanto della sorgente si vede adesso dentro la sagoma della pagina sopra: 0 senza dissolvenza o senza zoom. */
  internal fun zoomCrossfadeAlpha(): Float {
    if (!zoomCrossfade || reducedMotion) return 0f
    val journey = zoom ?: return 0f
    val shape = zoomShape() ?: return 0f
    return FluidZoom.crossfade(shape, fullZoomShape(), journey.source)
  }

  /**
   * Quanto si vede adesso il contenuto della pagina che ha il ruolo [role]: `1 − zoomCrossfadeAlpha` per la pagina sopra
   * durante uno zoom (lo stesso numero del `saveLayer` con cui la pagina sbiadisce dentro la sagoma), 1 altrimenti.
   *
   * È la curva di **tutto** ciò che è della pagina, anche di quello che non disegna lei: il titolo grande lo disegna la
   * barra dell'host, sopra le pagine, e lo sbiadisce con questo numero in un `saveLayer` grande quanto la sagoma, come la
   * pagina. Fino a corr-s4-zoom la barra lo ritagliava dentro la sagoma ma non lo sbiadiva, e
   * sul Tab S9 il «Faro» nero restava sulla card fino all'arrivo (≈480 ms) mentre il resto della pagina se n'era andato
   * verso 200 ms, come in Apple (S4, NO 4). Con la stessa curva la pagina se ne va intera, titolo compreso, col tasto, col
   * dito e all'apertura.
   */
  internal fun zoomContentAlpha(role: PageRole): Float = if (role == PageRole.Upper) 1f - zoomCrossfadeAlpha() else 1f

  /** La fotografia della sorgente della pagina sopra, se la variante l'ha chiesta e la sorgente si è già registrata. */
  internal fun zoomSnapshot(): FluidTransitionSnapshot? = upper?.let { zoomSources[it] }?.snapshot

  internal fun awaitingZoomDraw(page: Any): Boolean = page == upper && zoomSources[page]?.isConsumed == false

  internal fun zoomFrontDrawn(page: Any) {
    if (page != upper) return
    if (pendingPlacement == page && zoom != null) pagePlaced(page)
    else if (awaitingZoomDraw(page)) zoomSources.getValue(page).consume()
  }

  /**
   * Da dove a dove (x, px) la pagina sopra copre quella sotto in questo fotogramma.
   *
   * Una pagina che aspetta il primo posizionamento non copre niente (giro di correzione di F4A, A4): non è ancora
   * disegnata. Nel cambio di scheda la pagina che arriva parte al 13% della larghezza e da lì copriva già quasi tutto,
   * e il titolo grande di quella che esce (disegnato dalla barra, tagliato dove la pagina sopra copre) spariva un
   * fotogramma prima che la pagina nuova comparisse, col sottotitolo e il testo ancora fermi.
   */
  internal fun upperCoverage(): Pair<Float, Float> {
    if (pendingPlacement != null && pendingPlacement == upper) return width to width
    // Lo zoom copre un riquadro, non una striscia: per la barra (che sta in cima) copre solo quando la sagoma e' arrivata
    // sotto i suoi controlli.
    zoomShape()?.let { shape ->
      return if (shape.bounds.top <= barBottomPx + 0.5f) shape.bounds.left to shape.bounds.right else width to width
    }
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
    val request = pendingOpen
    pendingOpen = null
    if (request != null && change.kind == FluidStackChangeKind.Push && change.from == request.from &&
      processed.size == old.size + 1 && processed.dropLast(1) == old) {
      beginPush(request.from, processed.last(), request)
      return
    }
    when (change.kind) {
      FluidStackChangeKind.None -> if (phase == Phase.Idle) settle()
      FluidStackChangeKind.Pop -> beginPop(change.from!!, change.to!!)
      FluidStackChangeKind.Push, FluidStackChangeKind.Replace -> beginPush(change.from!!, change.to!!)
      FluidStackChangeKind.Peer -> beginPeer(change.from!!, change.to!!, change.direction)
    }
  }

  private fun beginPush(from: Any, to: Any, request: OpenRequest? = null) {
    if (phase == Phase.Popping && upper == to && lower == from && canReverseTravel() && renderable(to)) {
      // La stessa voce torna in cima prima di essere uscita: cambia il bersaglio, non il fotogramma.
      opening = true
      show(FluidPageMove.Push, from = from, to = to)
      phase = Phase.Pushing
      aimZoom(0f, null)
      animateTravel(0f, zoomSpring(), velocityPx) { settle() }
      return
    }
    val visibleSource = if (pendingPlacement == from) lower ?: from else from
    finishNow()
    upperTransition = transitionOf(to)
    upper = to
    lower = visibleSource.takeIf(renderable)
    vertical = request != null
    opening = true
    direction = layoutDirectionSign
    show(FluidPageMove.Push, from = visibleSource, to = to)
    travelPx = exitTravel()
    phase = Phase.Pushing
    // Lo zoom nasce dalla sorgente: la sagoma parte dal suo riquadro e dalla sua forma. Senza, l'Expand di sempre.
    zoomSourceShape(to)?.let { source ->
      growing = retainZoomSource(to)?.growingGlass
      landing = 0f
      sharedOrigins = findSharedOrigins(source.bounds)
      zoom = ZoomJourney(source = source, from = source, fromTravel = travelPx, to = null, toTravel = 0f)
    }
    if (request != null) {
      fingerOpens = true
      isInteractive = !request.released
      phase = Phase.Dragging
      pendingPlacement = to
      travelPx = height * request.exit
      velocityPx = request.velocity
      if (request.released) releaseVertical(request.token, request.velocity, request.cancelled) else markTouchFrame()
      return
    }
    if (!reducedMotion) animateTravel(0f, zoomSpring(), 0f) { settle() }
    if (phase == Phase.Pushing) pendingPlacement = to
  }

  /**
   * La prima misura può comporre tutta la pagina. La scheda parte qui per conservare il passo iniziale del 13%.
   * Per lo zoom il renderer segnala questo passaggio solo dopo aver disegnato la faccia o l'immagine condivisa.
   */
  internal fun pagePlaced(page: Any, placedAtNanos: Long? = null) {
    if (pendingPlacement != page) return
    pendingPlacement = null
    // La sorgente si nasconde nel fotogramma in cui la pagina compare sulla sua sagoma, non prima: nel fotogramma del
    // cambio di pila la pagina nuova non e' ancora composta, e una sorgente gia' nascosta lascerebbe un buco.
    if (zoom != null && page == upper) zoomSources[page]?.consume()
    if (phase == Phase.Peer) {
      runPeerClock(placedAtNanos)
    } else if (crossfading && !vertical) {
      animateTravel(0f, FluidPageMotion.page(), 0f) { settle() }
    }
  }

  private fun beginPop(from: Any, to: Any) {
    if (vertical && upper == from && pendingPlacement == null) {
      lower = to.takeIf(renderable)
      if (phase != Phase.Committing) verticalEndpoint(open = false, velocity = velocityPx)
      return
    }
    if (pendingPlacement == from && lower == to) {
      job?.cancel()
      job = null
      settle()
      return
    }
    if (pendingPlacement == from) {
      popUnplaced(from, to)
      return
    }
    if (upper == from && (phase == Phase.Committing || phase == Phase.Dragging || phase == Phase.Cancelling)) {
      // Il pop del gesto stesso (onBack l'ha appena chiesto), o la pagina tolta da altrove mentre
      // il dito la teneva: la molla continua da dove e', con la velocita' che ha.
      if (lower != to) lower = to.takeIf(renderable)
      if (phase != Phase.Committing) commit(velocityPx)
      return
    }
    if (phase == Phase.Pushing && upper == from && canReverseTravel() && renderable(from)) {
      closingBackdropCeiling = backdropArrival(from)
      // Nessun settle intermedio: le voci del push restano in transizione fino al vero riposo.
      lower = to.takeIf(renderable)
      opening = false
      show(FluidPageMove.Pop, from = from, to = to)
      phase = Phase.Popping
      aimZoom(exitTravel(), zoomSourceShape(from))
      animateTravel(exitTravel(), zoomSpring(), velocityPx) { settle() }
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
    startZoomBack(from, following = false)
    animateTravel(exitTravel(), zoomSpring(), 0f) { settle() }
  }

  /**
   * Un pop che arriva mentre la pagina che entrava ([from]) aspetta ancora il primo posizionamento, verso una pagina che
   * non è quella che si vede ([to] più indietro: `[R,A] → [R,A,B] → [R]`). La pagina che entrava non si è mai vista e
   * non esce: esce quella che si vede, la sotto, con lo stile suo (giro di correzione di F4A, C7). Prima il pop
   * ricominciava da [from] a corsa zero, e con `Expand` (che non inverte) la pagina mai posata compariva a piena vista
   * per uscire. Se la pagina che si vede non si può più disegnare, si va a riposo.
   */
  private fun popUnplaced(from: Any, to: Any) {
    val visible = lower
    finishNow()
    if (visible == null || visible == from || !renderable(visible)) {
      settle()
      return
    }
    upperTransition = transitionOf(visible)
    upper = visible
    lower = to.takeIf(renderable)
    opening = false
    direction = layoutDirectionSign
    show(FluidPageMove.Pop, from = visible, to = to)
    travelPx = 0f
    phase = Phase.Popping
    animateTravel(exitTravel(), FluidPageMotion.page(), 0f) { settle() }
  }

  /**
   * Expand cambia scala e origine fra i due versi: nessuna corsa di chiusura ricrea l'apertura. Lo zoom invece va e torna
   * sulla stessa sagoma ripartendo da quella che si vede, e si inverte come Slide.
   */
  private fun canReverseTravel(): Boolean = when (upperTransition) {
    // Questi stili ignorano opening in pageFrame: la corsa di ripresa e' gia' quella corretta.
    FluidPageTransition.Slide, FluidPageTransition.ShrinkSlide -> true
    FluidPageTransition.Expand -> zoom != null
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
    if (scope == null) {
      settle()
      return
    }
    // Il passo parte dal primo posizionamento della scheda che arriva (A4): vedi [pagePlaced].
    pendingPlacement = to
  }

  /** Il passo fra schede, dal primo posizionamento della scheda che arriva. */
  private fun runPeerClock(placedAtNanos: Long?) {
    val scope = scope ?: run {
      settle()
      return
    }
    job?.cancel()
    job = scope.launch {
      if (crossfading) {
        Animatable(0f).animateTo(1f, FluidMotion.crossFade()) {
          peerTime = value
          peerEased = value
        }
      } else {
        // Il layout ha già mostrato il passo iniziale. Il primo vsync deve avanzarlo anche se
        // la posa ha occupato il resto del frame. Poi conta soltanto il clock di Compose.
        var previous: Long? = null
        var elapsedMs = 0f
        do {
          withFrameNanos { frame ->
            val delta = previous?.let { frame - it } ?: placedAtNanos?.let { System.nanoTime() - it } ?: 0L
            previous = frame
            val scale = coroutineContext[MotionDurationScale]?.scaleFactor ?: 1f
            elapsedMs += if (scale > 0f) delta.coerceAtLeast(0L) / (1_000_000f * scale) else Float.POSITIVE_INFINITY
            peerTime = (elapsedMs / FluidMotion.DurationPeer).coerceIn(0f, 1f)
            peerEased = FluidMotion.EaseEmphasized.transform(peerTime)
          }
        } while (peerTime < 1f)
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
    closingBackdropCeiling = 1f
    val top = processed.lastOrNull()
    pendingPlacement = null
    phase = Phase.Idle
    touchClock?.cancel()
    touchClock = null
    lastTravelFrame = null
    vertical = false
    settlingOpen = null
    catchOffset = 0f
    catchVelocity = 0f
    sourceNeedsPlacement = false
    pendingOpen = null
    zoom = null
    sharedOrigins = emptyList()
    growing = null
    landing = 0f
    // La sorgente di una pagina che non c'e' piu' rinasce adesso: nello stesso fotogramma la pagina, gia' rientrata nella
    // sagoma, smette di disegnarsi (il ruolo si legge dallo stato).
    if (zoomSources.isNotEmpty()) {
      val gone = zoomSources.keys.filter { it !in processed }
      for (page in gone) zoomSources.remove(page)?.release()
    }
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
    pendingPlacement = null
    job?.cancel()
    job = null
    val scope = scope
    if ((reducedMotion && !crossfading) || scope == null) {
      // Nel gesto ridotto il dito ha seguito 1:1 e il rilascio si posa subito.
      travelPx = target
      velocityPx = 0f
      onEnd()
      return
    }
    val last = if (vertical) lastTravelFrame else null
    val animation = if (last == null) AnimationState(travelPx, initialVelocity)
    else AnimationState(travelPx, initialVelocity, last)
    val originalExtent = exitTravel()
    velocityPx = initialVelocity
    job = scope.launch {
      try {
        animation.animateTo(target, if (crossfading) FluidMotion.crossFade() else spec, sequentialAnimation = last != null) {
          lastTravelFrame = lastFrameTimeNanos
          val resize = if (originalExtent > 0f) exitTravel() / originalExtent else 1f
          travelPx = value * resize
          velocityPx = velocity * resize
          if (zoom != null && target == 0f && zoomShape()?.let {
              FluidZoom.distance(it, fullZoomShape()) <= FluidZoom.ArrivalDp * density
            } == true) {
            travelPx = 0f
            throw ZoomArrived()
          }
          if (zoomArrived(travelPx)) throw ZoomArrived()
        }
      } catch (_: ZoomArrived) {
        // La sagoma è sulla sorgente: il movimento finisce qui, e la sorgente rinasce in questo fotogramma.
      }
      velocityPx = 0f
      if (target == 0f && zoom != null && landing < 1f) {
        travelPx = 0f
        if (growing != null || zoomShape()?.corners != cardRadii) {
          val duration = if (growing != null) FluidPageMotion.GrowSolidMillis else FluidPageMotion.ZoomCornerMillis
          AnimationState(landing).animateTo(1f, tween(duration, easing = LinearEasing)) { landing = value }
        }
      }
      job = null
      onEnd()
    }
  }

  /**
   * Lo zoom che torna nella sorgente è finito quando la sagoma ci è arrivata (entro [FluidZoom.ArrivalDp]), non quando la
   * corsa scende sotto mezzo pixel: la coda della molla critica teneva la pagina in miniatura ferma sulla card per un
   * quinto di secondo prima che la card rinascesse (banco, `zoom-dito-interrotto`). Solo verso la sorgente: l'apertura e
   * gli altri stili finiscono come sempre.
   */
  private fun zoomArrived(travel: Float): Boolean {
    val journey = zoom ?: return false
    if (vertical) {
      if (phase != Phase.VerticalSettling || settlingOpen != false) return false
      val shape = zoomShape() ?: return false
      return FluidZoom.distance(shape, journey.source) <= FluidZoom.ArrivalDp * density
    }
    val to = journey.to ?: return false
    if (journey.following || journey.toTravel <= journey.fromTravel) return false
    val left = 1f - FluidZoom.travelFraction(travel, journey.fromTravel, journey.toTravel)
    return left * FluidZoom.distance(journey.from, to) <= FluidZoom.ArrivalDp * density
  }

  /** Il segnale con cui [animateTravel] ferma la molla di uno zoom arrivato: non è una cancellazione del lavoro. */
  private class ZoomArrived : CancellationException("lo zoom e' arrivato nella sorgente")

  // --- Il dito -------------------------------------------------------------------------------------

  /** Un contatto verticale. Il gettone sopravvive al giro di composizione che pubblica il push. */
  internal fun beginVertical(onOpen: (() -> Unit)?): Int? {
    if (height <= 0f || width <= 0f || scope == null || pendingOpen != null) return null
    if (canRetakeVertical) {
      job?.cancel()
      job = null
      phase = Phase.Dragging
      settlingOpen = null
      isInteractive = true
      catchVerticalVelocity()
      markTouchFrame()
      return ++dragToken
    }
    if (onOpen != null) {
      val origin = interactionPage ?: return null
      if (phase != Phase.Idle) return null
      val request = OpenRequest(origin, ++dragToken)
      pendingOpen = request
      try {
        onOpen()
      } catch (failure: Throwable) {
        pendingOpen = null
        throw failure
      }
      return request.token
    }
    if (!canDismissDown) return null
    val exit = upperExitFraction()
    val extent = exitTravel()
    val speed = if (extent > 0f) velocityPx / extent else 0f
    val shape = zoomShape()
    val token = beginDrag(layoutDirectionSign) ?: return null
    if (zoom == null && !reducedMotion && upper in ownPageBackground && upperTransition == FluidPageTransition.Expand) {
      val full = fullZoomShape()
      zoom = ZoomJourney(FluidZoom.pinchWithoutSource(full.bounds), full, 0f, null, height, withoutSource = true)
    }
    if (shape != null && exit > 0f && exit < 1f) zoom = zoom?.copy(from = shape, viaExit = exit)
    vertical = true
    fingerOpens = false
    currentTravel = height * exit
    velocityPx = height * speed
    catchVerticalVelocity()
    markTouchFrame()
    return token
  }

  // Il dito e la molla leggono il medesimo clock Compose (anche nei test): il primo vsync
  // dopo il rilascio continua dall'ultimo mostrato. Nessuna lettura di wall clock, nessuna
  // scrittura di stato osservabile e nessun disegno in più mentre il dito è fermo.
  private fun markTouchFrame() {
    touchMoved = true
    if (touchClock?.isActive == true) return
    touchClock = scope?.launch {
      while (true) {
        val sampled = withFrameNanos { frame ->
          val moved = touchMoved
          touchMoved = false
          if (moved) lastTravelFrame = frame else if (catchVelocity == 0f) lastTravelFrame = null
          moved
        }
        if (!sampled) break
      }
    }
  }

  private fun catchVerticalVelocity() {
    catchOffset = 0f
    catchVelocity = velocityPx / height
    if (velocityPx == 0f || reducedMotion) return
    val extent = height
    val initial = velocityPx
    val last = lastTravelFrame
    val residual = if (last == null) AnimationState(0f, initial) else AnimationState(0f, initial, last)
    job = scope?.launch {
      // Riparte dal vsync già mostrato. Azzerare il tempo al prossimo vsync duplicherebbe la posa
      // della presa per un frame anche con una velocità ereditata di migliaia di px/s.
      residual.animateTo(0f, FluidPageMotion.zoom(), sequentialAnimation = last != null) {
        lastTravelFrame = lastFrameTimeNanos
        val offset = value / extent
        val speed = velocity / extent
        travelPx = (travelPx + (offset - catchOffset) * height).coerceIn(0f, height)
        velocityPx += (speed - catchVelocity) * height
        catchOffset = offset
        catchVelocity = speed
      }
    }
  }

  /** Delta fisico in px dell'host fermo. Una rotazione non invalida l'ancora del prossimo evento. */
  internal fun moveVertical(token: Int, deltaY: Float, velocityY: Float) {
    val request = pendingOpen
    if (request?.token == token && !request.released && height > 0f) {
      request.exit = (request.exit + deltaY / height).coerceIn(0f, 1f)
      request.velocity = velocityY
    } else if (vertical && isDragging(token)) {
      travelPx = (travelPx + deltaY).coerceIn(0f, height)
      velocityPx = velocityY + catchVelocity * height
      markTouchFrame()
    }
  }

  /** Pinch geometry is expressed in stationary host pixels. The same journey continues at release. */
  internal fun movePinch(token: Int, scale: Float, anchor: Offset, center: Offset) {
    if (!vertical || !isDragging(token) || reducedMotion) return
    val full = fullZoomShape()
    fun mapped(point: Offset) = center + (point - anchor) * scale
    val shape = FluidZoomShape(Rect(mapped(full.bounds.topLeft), mapped(full.bounds.bottomRight)))
    // An interior waypoint also represents a pure two-finger translation at scale one.
    val shift = (center - anchor).getDistance() / full.bounds.size.maxDimension
    val exit = maxOf(kotlin.math.abs(1f - scale), shift).coerceIn(.001f, .999f)
    val journey = zoom ?: return
    zoom = journey.copy(from = shape, viaExit = exit, pinched = true)
    travelPx = height * exit
    velocityPx = 0f
    markTouchFrame()
  }

  internal fun releaseVertical(token: Int, velocity: Float, cancelled: Boolean = false, decision: Boolean? = null) {
    val request = pendingOpen
    if (request?.token == token) {
      request.released = true
      request.velocity = velocity
      request.cancelled = cancelled
      // Come finishCommit: il callback può rifiutare la navigazione. Non armare il push successivo.
      scope?.launch {
        repeat(2) { withFrameNanos { } }
        if (pendingOpen === request) pendingOpen = null
      }
      return
    }
    if (!vertical || !isDragging(token)) return
    isInteractive = false
    val speed = velocity + catchVelocity * height
    if (fingerOpens) {
      val open = !cancelled && FluidPageMotion.backRelease(height - travelPx, -velocity, height, density)
      verticalEndpoint(open, speed)
    } else if (!cancelled && (decision ?: verticalDismissRelease[upper]?.invoke(travelPx, velocity)
        ?: FluidPageMotion.backRelease(travelPx, velocity, height, density))) {
      confirmThroughDispatcher(speed)
    } else {
      verticalEndpoint(open = true, speed)
    }
  }

  private fun verticalEndpoint(open: Boolean, velocity: Float) {
    touchClock?.cancel()
    touchClock = null
    settlingOpen = open
    phase = Phase.VerticalSettling
    isInteractive = false
    val departing = upper
    if (!open && departing != null) {
      val source = zoomSourceShape(departing)
      val journey = zoom
      val at = upperExitFraction()
      if (source != null && journey != null && source != journey.source && at < 1f) {
        val visible = zoomShape() ?: journey.from
        zoom = journey.copy(source = source, from = visible, viaExit = at.takeIf { it > 0f })
      }
    }
    val placement = pendingPlacement
    animateTravel(if (open) 0f else height, zoomSpring(), velocity) {
      if (open) {
        settle()
      } else {
        phase = Phase.Committing
        if (departing != null && departing in processed) onBack()
        finishCommit(departing)
      }
    }
    if (phase == Phase.VerticalSettling) pendingPlacement = placement
  }

  /** Se adesso un gesto indietro puo' cominciare (o riprendere una pagina che torna a posto). */
  internal fun canStartGesture(): Boolean {
    if (vertical || pendingOpen != null || width <= 0f || processed.size < 2) return false
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
  internal fun beginDrag(direction: Int, takeOver: Boolean = false): Int? {
    if (vertical) return null
    // Il back di sistema si prende il dito che l'app stava gia' seguendo (navigazione a gesti, 3.0.4):
    // stessa corsa, stesse pagine, gettone nuovo. Il gesto dell'app se ne accorge al prossimo evento
    // (`isDragging` col suo gettone e' falso) e beve il resto senza rilasciare niente.
    val handOver = takeOver && phase == Phase.Dragging
    if (!handOver && !canStartGesture()) return null
    val interruptPush = phase == Phase.Pushing && resumesTravelOnDrag()
    pendingPlacement = null
    if (handOver || phase == Phase.Cancelling || interruptPush) {
      // Ripresa al volo: corsa, verso e pagine restano quelli di prima.
      job?.cancel()
      job = null
      if (interruptPush) {
        opening = false
        onTransitionStart(listOfNotNull(upper, lower))
      }
      // Lo zoom ripreso dal dito riparte dalla sagoma che si vede, sotto il punto toccato. Il sistema che si prende il
      // dito dell'app a meta' gesto continua lo stesso gesto, senza ripartire.
      if (!handOver) followZoom()
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
      startZoomBack(top, following = true)
    }
    phase = Phase.Dragging
    show(FluidPageMove.Back, from = upper, to = lower)
    isInteractive = true
    return ++dragToken
  }

  internal fun isDragging(token: Int): Boolean = phase == Phase.Dragging && dragToken == token

  internal val isHorizontalDrag get() = phase == Phase.Dragging && !vertical

  /** La pagina e' sotto un dito, o sta tornando a posto da un gesto: un back nuovo la prende da dov'e'. */
  internal fun isMovingByFinger(): Boolean = phase == Phase.Dragging || phase == Phase.Cancelling

  /**
   * Il primo evento deve misurarsi dal punto della presa quando beginDrag conserva la corsa.
   * Non autorizza il contatto: bordo, barra e modalita' restano responsabilita' del riconoscitore.
   */
  internal fun resumesTravelOnDrag(): Boolean = isMovingByFinger() ||
    (phase == Phase.Pushing && upper == processed.lastOrNull() && canReverseTravel())

  /** Snap sincrono: il dito non aspetta nessuna molla. */
  internal fun dragTo(token: Int, travel: Float) {
    if (!isDragging(token)) return
    travelPx = travel.coerceIn(0f, exitTravel())
  }

  /**
   * Il dito si e' alzato. [velocity] in px/s lungo l'uscita; [decided] e' la decisione del sistema
   * (il back di sistema conferma o annulla da se'), null per la regola del rilascio in-app.
   *
   * **Il dito dentro l'app non toglie la pagina da se': la chiede** (3.0). Oltre la soglia la sua
   * conferma e' un "indietro" mandato al dispatcher, esattamente quello che il sistema manda a fine
   * gesto ([confirmThroughDispatcher]): se non c'e' un `BackHandler` abilitato dopo l'host risponde
   * l'host e la pagina esce come sempre; se c'e', risponde lui e la pagina torna sotto il dito. Un
   * rilascio sotto la soglia e' un gesto annullato e non chiama nessuno. Il back di sistema ci e'
   * gia' passato (e' lui a dirci [decided]).
   */
  internal fun release(token: Int, velocity: Float, decided: Boolean? = null) {
    if (!isDragging(token)) return
    isInteractive = false
    val confirmed = decided ?: FluidPageMotion.backRelease(travelPx, velocity, width, density)
    when {
      !confirmed -> cancelGesture(velocity)
      decided == null -> confirmThroughDispatcher(velocity)
      else -> commit(velocity)
    }
  }

  /**
   * Chiede il back al dispatcher invece di togliere la pagina: chi risponde per primo e' l'ultimo
   * gestore abilitato, e l'host (registrato prima delle pagine) e' sotto i loro. Senza dispatcher, o
   * con il gestore dell'host spento (non dovrebbe: un gesto parte solo con due pagine), la pagina
   * esce come prima.
   *
   * La risposta e' sincrona: [dispatchBack] torna dopo che il gestore ha girato. Se l'host ha risposto
   * [requestBack] ha gia' preso la velocita' e fatto partire la molla di uscita; se la velocita' e'
   * ancora qui, ha risposto un altro gestore e la pagina torna dov'era, con la molla di un annullo. Se
   * quel gestore toglie la pagina da se' (un "salva e chiude"), la pila si accorcia al fotogramma
   * dopo e [beginPop] riconosce il pop come quello del gesto: la molla riprende da dove e' arrivata.
   */
  private fun confirmThroughDispatcher(velocity: Float) {
    val dispatch = dispatchBack
    if (dispatch == null || backHandler?.isBackEnabled != true) {
      commit(velocity)
      return
    }
    awaitingAnswer = velocity
    try {
      dispatch()
    } finally {
      if (awaitingAnswer != null) {
        awaitingAnswer = null
        if (phase == Phase.Dragging) cancelGesture(velocity)
      }
    }
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
    if (vertical) {
      verticalEndpoint(open = false, velocity)
      return
    }
    phase = Phase.Committing
    show(FluidPageMove.Back, from = upper, to = lower)
    isInteractive = false
    val departing = upper
    // Lo zoom torna nella sorgente da dove il dito l'ha lasciato; se la sorgente e' sparita, dove l'aveva trovata.
    aimZoom(exitTravel(), departing?.let(::zoomSourceShape))
    // Prima onBack, poi la molla: la pila si accorcia mentre la pagina vola, e [beginPop] riconosce
    // il pop come il proprio invece di ricominciare.
    if (departing != null && departing in processed) onBack()
    animateTravel(exitTravel(), zoomSpring(), velocity) { finishCommit(departing) }
  }

  /**
   * La molla della corsa: quella dello zoom misurata su Apple se una sagoma viaggia fra la sorgente e la pagina (push,
   * pop, conferma del dito, le inversioni), altrimenti quella delle pagine (Slide, ShrinkSlide e l'Expand senza sorgente
   * restano al bit come in F4A).
   */
  private fun zoomSpring() = when {
    growing != null && opening && !vertical -> FluidPageMotion.growTap
    zoom != null -> FluidPageMotion.zoom()
    else -> FluidPageMotion.page()
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
        if (vertical) verticalEndpoint(open = true, velocity = 0f)
        else {
          phase = Phase.Cancelling
          aimZoom(0f, null)
          animateTravel(0f, FluidPageMotion.pageReturn(), 0f) { settle() }
        }
      } else {
        settle()
      }
    }
  }

  private fun cancelGesture(velocity: Float) {
    if (vertical) {
      verticalEndpoint(open = true, velocity)
      return
    }
    phase = Phase.Cancelling
    aimZoom(0f, null)
    animateTravel(0f, FluidPageMotion.pageReturn(), velocity) { settle() }
  }

  // --- Lo zoom, fase per fase -----------------------------------------------------------------------------------

  /**
   * La sagoma della sorgente di [page] nelle coordinate dell'host, o null: senza sorgente dichiarata, con le animazioni
   * ridotte, con uno stile diverso da Expand, o se la sorgente adesso non si trova (uscita dallo scorrimento, pagina
   * cambiata). Si cerca anche nella pagina coperta: al ritorno la sorgente sta lì.
   */
  private fun zoomSourceShape(page: Any): FluidZoomShape? {
    if (reducedMotion || transitionOf(page) != FluidPageTransition.Expand) return null
    val geometry = sourceOf(page)?.let(resolveSource) ?: return null
    val layoutDirection = if (layoutDirectionSign < 0) LayoutDirection.Rtl else LayoutDirection.Ltr
    return FluidZoom.shapeOf(geometry, hostOriginInRoot, Density(density), layoutDirection).takeIf { it.isUsable }
  }

  /**
   * Un ritorno che parte da riposo (il pop, o il dito che prende la pagina): con la sorgente è uno zoom verso di lei, e
   * la sorgente si nasconde subito (la pagina sotto si disegna da questo fotogramma). Senza, il ripiego: la chiusura di
   * Expand di sempre (rimpicciolisce e scivola), che da riposo parte dallo stesso fotogramma, senza salti.
   */
  private fun startZoomBack(page: Any, following: Boolean) {
    zoom = null
    val source = zoomSourceShape(page) ?: return
    growing = sourceOf(page)?.growingGlass
    landing = 1f
    sharedOrigins = findSharedOrigins(source.bounds)
    zoom = ZoomJourney(
      source = source,
      from = fullZoomShape(),
      fromTravel = travelPx,
      to = if (following) null else source,
      toTravel = if (following) travelPx else exitTravel(),
      following = following,
      pivot = touchPoint(),
      returnDim = true,
    )
    retainZoomSource(page)?.consume()
  }

  /**
   * La fase cambia bersaglio: la sagoma riparte da quella che si vede adesso, verso la sorgente se [toTravel] è
   * l'uscita, altrimenti verso la pagina intera. [found] è la sorgente ritrovata adesso; null, si va dove la si era
   * trovata l'ultima volta (è sparita nel frattempo: nessun salto, la sagoma atterra dov'era).
   */
  private fun aimZoom(toTravel: Float, found: FluidZoomShape?) {
    val journey = zoom ?: return
    val towardSource = toTravel > 0f
    val source = if (towardSource) found ?: journey.source else journey.source
    zoom = journey.copy(
      source = source,
      from = zoomShape() ?: journey.from,
      fromTravel = travelPx,
      to = if (towardSource) source else null,
      toTravel = toTravel,
      following = false,
    )
  }

  /** Il dito prende la sagoma dov'è: da qui la segue, attorno al punto toccato. */
  private fun followZoom() {
    val journey = zoom ?: return
    zoom = journey.copy(
      from = zoomShape() ?: journey.from,
      fromTravel = travelPx,
      to = null,
      toTravel = travelPx,
      following = true,
      pivot = touchPoint(),
    )
  }

  /** Il punto dell'ultimo contatto, in px dell'host (il centro se non ce n'è stato uno). */
  private fun touchPoint(): Offset = Offset(expandOrigin.pivotFractionX * width, expandOrigin.pivotFractionY * height)

  private fun retainZoomSource(page: Any): FluidTransitionSource? {
    val source = sourceOf(page) ?: return null
    val previous = zoomSources[page]
    if (previous?.sameOrigin(source) == true) return previous
    // A paged destination can return to a different thumbnail than the one which opened it.
    previous?.release()
    // La fotografia serve solo alla dissolvenza: senza, nessun layer in più.
    if (zoomCrossfade && !reducedMotion) source.prepareSnapshot()
    zoomSources[page] = source
    return source
  }

  /** L'host esce di scena: le sorgenti nascoste tornano tutte. */
  internal fun releaseSources() {
    observedContact = null
    openContact = null
    openAction = null
    for (source in zoomSources.values) source.release()
    zoomSources.clear()
  }

  /**
   * Back senza gesto (3 tasti, Android 13 e prima, tastiera): l'app toglie, la molla accompagna. E' anche
   * la risposta dell'host a un back che un dito in-app ha confermato ([confirmThroughDispatcher]):
   * allora la pagina e' ancora sotto il dito, e esce con la sua velocita' invece di apparire sparita.
   */
  internal fun requestBack() {
    val fingerVelocity = awaitingAnswer
    awaitingAnswer = null
    if (fingerVelocity != null && phase == Phase.Dragging) {
      commit(fingerVelocity)
      return
    }
    if (processed.size > 1) onBack()
  }

  /** Durante una conferma la pagina che esce non prende tocchi; durante un push, quella coperta. */
  internal fun blocksInput(page: Any): Boolean = when (phase) {
    Phase.Committing, Phase.Popping -> page == upper
    Phase.Pushing, Phase.Peer -> page == lower
    Phase.Dragging, Phase.VerticalSettling -> vertical && page == lower
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
    val bounds = coordinates.boundsInWindow()
    hostOriginInRoot = coordinates.positionInRoot()
    hostCoordinates = coordinates
    hostLeftInWindow = bounds.left
    hostRightInWindow = bounds.right
    val root = view.rootView
    windowWidth = root.width.toFloat()
    resizePage(coordinates.size.width.toFloat(), coordinates.size.height.toFloat())
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

  /** La geometria cambia, la fase, il gettone del dito e la velocità in frazioni/s restano gli stessi. */
  internal fun resizePage(newWidth: Float, newHeight: Float) {
    if (width == newWidth && height == newHeight) return
    if (isMoving && (newWidth <= 0f || newHeight <= 0f)) return
    val oldWidth = width
    val oldHeight = height
    val oldExtent = exitTravel()
    width = newWidth
    height = newHeight
    if (!isMoving || oldExtent <= 0f || oldWidth <= 0f || oldHeight <= 0f || newWidth <= 0f || newHeight <= 0f) return
    val scale = exitTravel() / oldExtent
    currentTravel *= scale
    velocityPx *= scale
    val journey = zoom ?: return
    sourceNeedsPlacement = true
    val sx = newWidth / oldWidth
    val sy = newHeight / oldHeight
    fun remap(shape: FluidZoomShape): FluidZoomShape = shape.resized(sx, sy)
    val source = upper?.let(::zoomSourceShape) ?: remap(journey.source)
    zoom = journey.copy(source = source, from = if (journey.from == journey.source) source else remap(journey.from),
      to = journey.to?.let { if (it == journey.source) source else remap(it) },
      fromTravel = journey.fromTravel * scale, toTravel = journey.toTravel * scale,
      pivot = Offset(journey.pivot.x * sx, journey.pivot.y * sy))
  }

  /** onPlaced dell'host precede quello dei figli. Dopo il layout possiamo leggere la sorgente riposizionata. */
  internal fun refreshPlacedSource() {
    if (!sourceNeedsPlacement) return
    sourceNeedsPlacement = false
    val journey = zoom ?: return
    val source = upper?.let(::zoomSourceShape) ?: return
    sharedOrigins = findSharedOrigins(source.bounds)
    zoom = journey.copy(source = source, from = if (journey.from == journey.source) source else journey.from,
      to = if (journey.to == journey.source) source else journey.to)
  }

  /** La nuova Activity ritrova le chiavi della pila, mai riferimenti alla vecchia vista o alle sue voci. */
  internal fun rotationSnapshot(key: (Any) -> Any): VerticalPageRotation? {
    if (!vertical || height <= 0f || phase == Phase.Committing) return null
    val top = upper ?: return null
    val below = lower ?: return null
    return VerticalPageRotation(key(top), key(below), upperExitFraction(), velocityPx / height,
      fingerOpens, settlingOpen, width, height, zoom, landing)
  }

  internal fun resumeRotation(key: (Any) -> Any) {
    val saved = restoreAfterRotation ?: return
    if (!initialized || height <= 0f || width <= 0f || processed.isEmpty()) return
    restoreAfterRotation = null
    val top = upper ?: return
    val below = lower ?: return
    if (phase != Phase.Idle || key(top) != saved.upper || key(below) != saved.lower) return
    vertical = true
    opening = saved.opening
    fingerOpens = saved.opening
    if (opening) show(FluidPageMove.Push, from = below, to = top) else show(FluidPageMove.Back, from = top, to = below)
    travelPx = saved.exit * height
    velocityPx = saved.speed * height
    if (!reducedMotion) saved.zoom?.let { old ->
      fun remap(shape: FluidZoomShape): FluidZoomShape = shape.resized(width / saved.width, height / saved.height)
      growing = retainZoomSource(top)?.growingGlass
      landing = saved.landing
      sharedOrigins = findSharedOrigins((zoomSourceShape(top) ?: remap(old.source)).bounds)
      zoom = old.copy(source = zoomSourceShape(top) ?: remap(old.source), from = remap(old.from))
      sourceNeedsPlacement = true
    }
    onTransitionStart(listOf(top, below))
    pendingPlacement = top
    phase = Phase.Dragging
    // Android annulla il puntatore insieme alla vecchia finestra. La molla prosegue con posizione
    // e velocità ritrovate, ed è riprendibile dal primo contatto nella nuova finestra.
    if (saved.target != null) verticalEndpoint(saved.target, velocityPx)
    else releaseVertical(++dragToken, velocityPx)
  }
}

internal data class VerticalPageRotation(
  val upper: Any, val lower: Any, val exit: Float, val speed: Float, val opening: Boolean,
  val target: Boolean?, val width: Float, val height: Float, val zoom: ZoomJourney?, val landing: Float = 0f,
)

private fun FluidZoomShape.resized(sx: Float, sy: Float): FluidZoomShape = copy(bounds = bounds.run {
  Rect(left * sx, top * sy, right * sx, bottom * sy)
})

/** Uno stato per host, ricordato. */
@Composable
fun rememberFluidPageHostState(): FluidPageHostState = remember { FluidPageHostState() }

/**
 * Un tratto del viaggio della sagoma di uno zoom fra pagine (F5B.3): da [from], la sagoma che si vedeva quando la fase
 * è cominciata (a corsa [fromTravel]), verso [to] a corsa [toTravel] (null: la pagina intera). Con [following] il dito la
 * tiene, attorno a [pivot]. [source] è la sorgente com'era l'ultima volta che la si è trovata: il ritorno ci va anche se
 * nel frattempo è sparita.
 */
@Immutable
internal data class ZoomJourney(
  val source: FluidZoomShape,
  val from: FluidZoomShape,
  val fromTravel: Float,
  val to: FluidZoomShape?,
  val toTravel: Float,
  val following: Boolean = false,
  val pivot: Offset = Offset.Zero,
  val viaExit: Float? = null,
  val pinched: Boolean = false,
  val withoutSource: Boolean = false,
  /** Vero per uno zoom nato come ritorno. Si conserva nelle riprese e nella rotazione. */
  val returnDim: Boolean = false,
)
