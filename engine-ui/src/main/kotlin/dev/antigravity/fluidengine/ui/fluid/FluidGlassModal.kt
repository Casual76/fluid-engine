package dev.antigravity.fluidengine.ui.fluid

import androidx.activity.compose.PredictiveBackHandler
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.focusable
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.ProvidableCompositionLocal
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.CornerRadius
import dev.antigravity.fluidengine.ui.fluidphysics.FluidCornerRadii
import dev.antigravity.fluidengine.ui.fluidphysics.FluidForm
import dev.antigravity.fluidengine.ui.fluidphysics.FluidPhysicsState
import dev.antigravity.fluidengine.ui.fluidphysics.fluidPhysicsSurface
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.layer.GraphicsLayer
import androidx.compose.ui.graphics.layer.drawLayer
import androidx.compose.ui.graphics.rememberGraphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.isTraversalGroup
import androidx.compose.ui.semantics.hideFromAccessibility
import androidx.compose.ui.semantics.paneTitle
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.Velocity
import androidx.compose.ui.unit.dp
import androidx.compose.ui.util.lerp
import dev.antigravity.fluidengine.ui.glass.interaction.GlassTouchHighlight
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.coroutines.cancellation.CancellationException
import kotlin.math.abs
import kotlin.math.roundToInt

/**
 * A modal that lives *inside* the app's composition.
 *
 * The reason this exists is a system boundary, not a shortcoming. [FluidSheet] is a `ModalBottomSheet`
 * and [FluidAlert] is a `Dialog`: both open a **separate platform window**, and a separate window
 * cannot read the app's `GraphicsLayer`. That is why they fall back to painted gradients, and it is
 * not fixable from this side. A pop-up that keeps the page visible and *refracted* behind it has to
 * be composed with the page.
 *
 * The precedent was already here and already shipping: [FluidNotificationHost] is an in-root overlay
 * that takes a [GlassBackdropState] and draws real glass. This is the same shape, with three things
 * it needs that a notification does not — a scrim, an expansion from the thing you touched, and a way
 * out.
 *
 * The awkward part is that the *feature* wants to declare the modal where its data lives (inside the
 * `NavHost`, next to the screen state) while the *pixel* has to come out above the tab bar. So
 * [FluidGlassModalPortal] declares and does not draw: it hands its content lambda to the host and the
 * host runs it at the root. The lambda executes inside the host's composition and reads the same
 * snapshot state the feature reads, so it stays live without anything being pushed through it.
 */
@Stable
class FluidGlassModalHostState internal constructor() {

  internal val entries = mutableStateListOf<FluidGlassModalEntry>()

  /**
   * Le ancore registrate da [fluidExpandOrigin], coi loro limiti e la loro registrazione.
   *
   * Esiste perche' "il tasto stesso diventa il pop-up" ha bisogno di due cose che il solo
   * rettangolo di `origin` non puo' dare: nascondere DAVVERO la riga mentre la finestra e' in
   * scena (non una dissolvenza accanto: la stessa sparizione secca del menu che si espande), e
   * un'immagine registrata della riga da far viaggiare con la finestra. Il layer del modale
   * ritrova l'ancora per coincidenza di rettangoli — e' la stessa geometria che `origin` riporta.
   */
  internal val expandAnchors = mutableStateListOf<FluidExpandAnchorNode>()

  private var sequence = 0

  /** Whether anything is currently open. An app can use it to obscure the page behind. */
  val isPresenting: Boolean get() = entries.any { it.visible }

  /**
   * Whether anything still has pixels on screen, the exit animation included.
   *
   * [isPresenting] turns false the instant a dismiss is *requested*, which is the right answer for
   * accessibility and the wrong one for anything the modal is borrowing from the page: give a
   * lifted row back at that moment and it reappears underneath a pane that is still folding into
   * it, for the whole length of the exit.
   */
  val isOnScreen: Boolean get() = entries.any { it.visible || it.lifted }

  internal fun register(entry: FluidGlassModalEntry) {
    if (entries.none { it === entry }) entries.add(entry)
  }

  internal fun unregister(entry: FluidGlassModalEntry) {
    entries.removeAll { it === entry }
  }

  /** The innermost open modal: the most recently *opened* one, not the most recently declared. */
  internal fun topEntry(): FluidGlassModalEntry? =
    entries.filter { it.visible }.maxByOrNull { it.openedAt }

  internal fun nextSequence(): Int = ++sequence

  /** Asks whatever is innermost to close. This is what back and a tap on the scrim call. */
  fun dismissTop() {
    topEntry()?.onDismissRequest?.invoke()
  }
}

/** Un'ancora di [fluidExpandOrigin]: dove sta, la sua immagine registrata, e chi la nasconde. */
@Stable
internal class FluidExpandAnchorNode(val layer: GraphicsLayer) {
  var bounds: Rect = Rect.Zero

  /**
   * Vero mentre la finestra nata da questa ancora e' in scena: la riga non disegna niente — e
   * nel primo draw da nascosta si registra nel [layer], che e' l'immagine che viaggia.
   */
  var hidden by mutableStateOf(false)
}

@Composable
fun rememberFluidGlassModalHostState(): FluidGlassModalHostState =
  remember { FluidGlassModalHostState() }

/** Lets any feature declare a modal without owning, or duplicating, the single root host. */
val LocalFluidGlassModalHostState: ProvidableCompositionLocal<FluidGlassModalHostState?> =
  staticCompositionLocalOf { null }

/** How a modal arranges itself once it has finished arriving. */
enum class FluidGlassModalPresentation {
  /**
   * A pop-up that opens **on the thing you touched**, sized to whatever it contains.
   *
   * The default, and the one this whole file exists for. It is not a dialog that happens to have a
   * source rectangle: it is placed against that rectangle, it grows out of the corner nearest it,
   * and it takes the width and height its content asks for rather than a fixed card size. A modal
   * that appears in the middle of the screen has already lost the connection to the tap that opened
   * it, and nothing done to it afterwards gets that back.
   */
  Popover,

  /** Anchored to the bottom edge, full width. For something tall enough to want the whole height. */
  Sheet,

  /** A list of actions anchored to the thing that was long-pressed. Placed by [fluidContextMenu]. */
  ContextMenu,

  /**
   * The pop-up **replaces** what opened it, in place.
   *
   * Where [Popover] appears *beside* the anchor, this one starts at exactly the anchor's rectangle
   * and grows out of it while the anchor itself steps aside — so what the eye follows is one object
   * changing size, not a second object arriving next to the first. It is the shape of a button that
   * unfolds into its own menu and folds back once something is chosen.
   */
  Expand,
}

/** One action in a context menu. */
@Immutable
data class FluidContextAction(
  val label: String,
  val icon: ImageVector? = null,
  /** Deletes, revokes, removes. Drawn in the error colour, and worth at most one per menu. */
  val destructive: Boolean = false,
  val enabled: Boolean = true,
  val onClick: () -> Unit,
)

@Stable
internal class FluidGlassModalEntry {
  var visible by mutableStateOf(false)
  var openedAt by mutableIntStateOf(0)
  var onDismissRequest: () -> Unit by mutableStateOf({})
  var origin: () -> Rect? by mutableStateOf({ null })
  var presentation by mutableStateOf(FluidGlassModalPresentation.Popover)
  var paneTitle: String? by mutableStateOf(null)
  var content: (@Composable ColumnScope.() -> Unit) by mutableStateOf({})

  /**
   * Whether the anchored element has been lifted into the overlay and must stop drawing itself.
   *
   * Separate from [visible] because it has to outlast it. It is set when the menu opens and cleared
   * only when the overlay has finished leaving — if the row came back the instant the dismiss was
   * requested, the last 130 ms of every exit would show the row and its own lifted copy at once,
   * one of them sliding.
   */
  var lifted by mutableStateOf(false)

  /** Context menus and expanding buttons: the actions to offer, and the picture of the row. */
  var preview: GraphicsLayer? by mutableStateOf(null)
  var previewSize: Rect? by mutableStateOf(null)
  var actions: List<FluidContextAction> by mutableStateOf(emptyList())
}

/**
 * Declares a modal here and draws it at the root.
 *
 * Nothing is emitted at the call site — this composable has no size and no pixels. It writes its
 * content into the host inside a `DisposableEffect` and takes it back out in `onDispose`, so a
 * feature that leaves the composition can never leave a modal stranded above the app.
 *
 * @param origin the rectangle to grow out of, in root coordinates — normally the bounds of the row
 *   that was tapped, captured with [fluidExpandOrigin]. Null opens from the centre.
 */
@Composable
fun FluidGlassModalPortal(
  visible: Boolean,
  onDismissRequest: () -> Unit,
  origin: () -> Rect? = { null },
  presentation: FluidGlassModalPresentation = FluidGlassModalPresentation.Popover,
  paneTitle: String? = null,
  content: @Composable ColumnScope.() -> Unit,
) {
  val host = LocalFluidGlassModalHostState.current ?: return
  val entry = remember { FluidGlassModalEntry() }

  // Written after composition succeeds rather than during it. The lambda is rebuilt on every
  // recomposition of the feature, so whatever it captured is never more than one frame old — and
  // what it *reads* is snapshot state, which the host subscribes to directly when it runs it.
  SideEffect {
    if (visible && !entry.visible) entry.openedAt = host.nextSequence()
    entry.visible = visible
    entry.onDismissRequest = onDismissRequest
    entry.origin = origin
    entry.presentation = presentation
    entry.paneTitle = paneTitle
    entry.content = content
  }

  DisposableEffect(host, entry) {
    host.register(entry)
    onDispose { host.unregister(entry) }
  }
}

/**
 * Declares a modal that presents **an item**, and stays whole while it leaves.
 *
 * This is the form to use when the modal shows a selected thing — a detail, a record, a note — and
 * "open" means "something is selected". Passing `visible = x != null` plus a lambda that reads `x`
 * to the other overload cannot survive its own dismissal: the compiler gives that lambda one
 * mutable instance whose captures are swapped in place, so on the frame the selection clears the
 * content composes to nothing and the pane would leave the screen as a hard cut, its text gone
 * while the scrim is still fading. Here the portal keeps the last non-null [item] itself and keeps
 * handing it to [content] for as long as the exit runs, so the pane leaves exactly as it stood.
 */
@Composable
fun <T : Any> FluidGlassModalPortal(
  item: T?,
  onDismissRequest: () -> Unit,
  origin: () -> Rect? = { null },
  presentation: FluidGlassModalPresentation = FluidGlassModalPresentation.Popover,
  paneTitle: String? = null,
  content: @Composable ColumnScope.(T) -> Unit,
) {
  var lastItem by remember { mutableStateOf(item) }
  if (item != null) lastItem = item
  FluidGlassModalPortal(
    visible = item != null,
    onDismissRequest = onDismissRequest,
    origin = origin,
    presentation = presentation,
    paneTitle = paneTitle,
  ) {
    lastItem?.let { content(it) }
  }
}

/**
 * Reports this element's bounds in root coordinates, for a modal to expand out of.
 *
 * The rectangle is what the eye was looking at when it tapped, and starting the pop-up anywhere else
 * costs the connection between the two. Cheap to leave on a row: it only reports when the row
 * actually moves.
 */
@Composable
fun Modifier.fluidExpandOrigin(
  /**
   * Whether the modal this element opens is on screen right now.
   *
   * While it is, the element **stops drawing**, and that is not tidiness: the pane that grew out of
   * it is glass, so the row underneath went on showing *through* it. Two rims one inside the other
   * where the card's edge should be, and the row's own title readable behind the pane's — which is
   * exactly what "the pop-up interpenetrates the button that opened it" looks like from the outside.
   *
   * The space stays reserved, because this is a draw and not a layout: nothing below the row moves
   * while the pane is up, so folding back into it lands on the same rectangle it left.
   */
  open: () -> Boolean = { false },
  onMeasured: (Rect) -> Unit,
): Modifier {
  val requested = open()
  // La dissolvenza legacy per chi passa `open`: resta onorata, ma il lavoro vero oggi lo fa il
  // registro delle ancore qui sotto — la finestra nasconde la riga DA SOLA, senza che il chiamante
  // debba cablare niente. E' cosi' che ogni app eredita la trasformazione aggiornando l'engine.
  val alpha = remember { Animatable(1f) }
  var grace by remember { mutableStateOf(false) }
  LaunchedEffect(requested) {
    if (requested) {
      grace = true
      alpha.animateTo(0f, FluidMotion.fadeOut(FluidAnchorFadeMillis))
    } else if (grace) {
      alpha.animateTo(1f, FluidMotion.fadeIn(FluidAnchorFadeMillis))
      grace = false
    }
  }

  // Il pezzo che mancava a "il tasto stesso diventa il pop-up": la riga si registra presso l'host,
  // si dipinge in un GraphicsLayer mentre un dito la tocca (lo stesso mestiere dell'ancora del
  // menu contestuale: il tocco che apre il modale e' anche quello che scatta la fotografia), e
  // quando la finestra e' in scena smette di disegnarsi DI COLPO — l'immagine continua il viaggio
  // nella finestra, quindi il cambio di proprietario non ha niente da mostrare.
  val host = LocalFluidGlassModalHostState.current
  val layer = rememberGraphicsLayer()
  val node = remember(layer) { FluidExpandAnchorNode(layer) }
  DisposableEffect(host, node) {
    host?.expandAnchors?.add(node)
    onDispose {
      node.hidden = false
      host?.expandAnchors?.remove(node)
    }
  }
  return this
    .onGloballyPositioned {
      val rect = it.boundsInRoot()
      node.bounds = rect
      onMeasured(rect)
    }
    .graphicsLayer { this.alpha = alpha.value }
    .drawWithContent {
      // In scena: la riga E' la finestra adesso, e disegnarla anche qui era il difetto numero
      // uno dell'apertura — "il tasto rimane li', non si e' trasformato". Lo spazio resta
      // riservato (e' un draw, non un layout), quindi il rientro atterra su un rettangolo vuoto.
      //
      // E la registrazione avviene QUI, nel primo draw da nascosta: niente registrazione al
      // tocco — un tap iniettato dura meno di un fotogramma e non lasciava mai un'istantanea
      // (layer 0x0, verificato a logcat). Nascondere invalida il draw, quindi questo ramo corre
      // nello stesso fotogramma in cui la finestra appare, sempre PRIMA che l'overlay la
      // ridisegni: l'immagine che parte col viaggio e' quella vera, di quel momento.
      if (node.hidden) {
        node.layer.record { this@drawWithContent.drawContent() }
        return@drawWithContent
      }
      drawContent()
    }
}

/** Quanto ci mette la riga a togliersi di mezzo, e a tornare (solo dissolvenza legacy di `open`). */
private const val FluidAnchorFadeMillis = 120

/**
 * Hides everything under it from accessibility while a modal is open.
 *
 * A translucent pop-up that still lets the focus walk into the page behind it is *worse* than an
 * opaque one: the page is visible, so nothing warns the person that what they just landed on is not
 * reachable. Apply it to whatever the app puts under [FluidGlassModalHost].
 */
@Composable
fun Modifier.fluidGlassModalObscured(): Modifier {
  val host = LocalFluidGlassModalHostState.current ?: return this
  if (!host.isPresenting) return this
  return this.semantics { hideFromAccessibility() }
}

/**
 * The host. One per app, at the root, next to [FluidNotificationHost].
 *
 * @param backdrop what the scrim refracts: the chrome's backdrop, which contains the whole page.
 */
@Composable
fun FluidGlassModalHost(
  state: FluidGlassModalHostState,
  backdrop: GlassBackdropState?,
  modifier: Modifier = Modifier,
) {
  if (state.entries.isEmpty()) return
  Box(modifier = modifier.fillMaxSize()) {
    // Every registered entry, in the order they were opened, so a menu raised from inside a modal
    // sits over it instead of replacing it. An entry that is closed and finished animating draws
    // nothing at all and costs one float animation.
    state.entries.sortedBy { it.openedAt }.forEach { entry ->
      key(entry) { FluidGlassModalLayer(entry = entry, backdrop = backdrop, host = state) }
    }
  }
}

/**
 * One open modal: its scrim, its material, and the physics that gets it on screen.
 *
 * The motion is deliberately the tab bar indicator's, not a dialog's. Two springs, one per axis,
 * with **different damping ratios** — 0.62 across, 0.74 down — because a single scale spring
 * produces a balloon and two mismatched ones produce a squash. That squash is the entire difference
 * between something that appeared and something that *arrived*, and it is what makes this read as
 * the same material as the pill you can pick up and drag in the navigation bar.
 */
@Composable
private fun FluidGlassModalLayer(
  entry: FluidGlassModalEntry,
  backdrop: GlassBackdropState?,
  host: FluidGlassModalHostState,
) {
  val reducedMotion = LocalFluidMotionPolicy.current.reducedMotion
  val haptics = LocalHapticFeedback.current
  // 0 = at the anchor's size, 1 = at its own. What "0" actually means in pixels is solved by the
  // layout below, per axis, from the two rectangles.
  val scaleX = remember { Animatable(0f) }
  val scaleY = remember { Animatable(0f) }
  val fade = remember { Animatable(0f) }

  // Fluid Engine, 1.9.5: tutto quello che nasce da un'ancora (pop-up con origine, Expand) apre la
  // finestra di Fluid-physics — la sagoma viaggia dall'ancora al pannello con la rifrazione
  // addosso. Il menu contestuale resta com'era, per scelta: la sua riga sollevata e' gia' la sua
  // storia. Il foglio pure.
  val journeyMode = backdrop != null &&
    entry.presentation != FluidGlassModalPresentation.ContextMenu &&
    entry.presentation != FluidGlassModalPresentation.Sheet

  LaunchedEffect(entry.visible, reducedMotion) {
    if (reducedMotion) {
      scaleX.snapTo(if (entry.visible) 1f else 0f)
      scaleY.snapTo(if (entry.visible) 1f else 0f)
      fade.snapTo(if (entry.visible) 1f else 0f)
      return@LaunchedEffect
    }
    if (entry.visible) {
      // Opacity arrives well before the geometry finishes. Tying them together makes the pop-up
      // fade *in* as it grows, which reads as a dissolve; separating them lets the shape do the
      // arriving while the content is already legible.
      launch { fade.animateTo(1f, FluidMotion.fadeIn(FluidPopoverFadeInMillis)) }
      if (journeyMode && entry.origin() != null) {
        // Il tocco che diventa una finestra merita la conferma sul dito — il menu contestuale ce
        // l'ha (LongPress) ed e' parte del perche' "e' piu' bello". Qui un click leggero: e' un
        // tocco secco, non una pressione.
        haptics.performHapticFeedback(HapticFeedbackType.ContextClick)
        // La formula, detta parola per parola: ACCELERAZIONE CONTINUA finche' non raggiunge il
        // bordo, poi oltre, frena, e torna alla posizione finale. Quindi: una rincorsa tutta in
        // ease-in che copre quasi l'intero viaggio ancora in accelerazione, e una molla che ne
        // eredita la velocita' — l'oltrepasso e' slancio vero, non un calcio dato alla fine.
        launch {
          scaleX.animateTo(
            FluidPopoverWindowRunUpEnd,
            tween(FluidPopoverWindowRunUpMillis, easing = FluidPopoverWindowRunUpEasing),
          )
          scaleX.animateTo(1f, spring(FluidPopoverWindowDampingX, FluidPopoverWindowStiffness, 0.001f))
        }
        launch {
          scaleY.animateTo(
            FluidPopoverWindowRunUpEnd,
            tween(FluidPopoverWindowRunUpMillis, easing = FluidPopoverWindowRunUpEasing),
          )
          scaleY.animateTo(1f, spring(FluidPopoverWindowDampingY, FluidPopoverWindowStiffness, 0.001f))
        }
      } else {
        launch { scaleX.animateTo(1f, spring(FluidPopoverDampingX, FluidPopoverStiffness, 0.001f)) }
        launch { scaleY.animateTo(1f, spring(FluidPopoverDampingY, FluidPopoverStiffness, 0.001f)) }
      }
    } else if (journeyMode && entry.origin() != null) {
      // La chiusura della finestra e' UN gesto: la sagoma rientra nella riga (piu' rigida che
      // altrove, cosi' atterra PRIMA che la dissolvenza finisca) e l'immagine della riga le
      // riappare dentro strada facendo — quando il layer si smonta, quello che c'e' sotto e'
      // gia' identico. La versione morbida chiudeva la dissolvenza con la sagoma ancora a meta'
      // corsa: il pannello svaniva PER ARIA e la riga ricompariva di colpo — "fa degli effetti
      // pop", ed era vero.
      launch { fade.animateTo(0f, FluidMotion.fadeOut(FluidPopoverJourneyFadeOutMillis)) }
      launch { scaleX.animateTo(0f, spring(1f, FluidPopoverJourneyExitStiffness, 0.001f)) }
      launch { scaleY.animateTo(0f, spring(1f, FluidPopoverJourneyExitStiffness, 0.001f)) }
    } else {
      // Leaving is critically damped: an overshoot on the way out reads as the pop-up bouncing off
      // the screen rather than being put away. But not *stiff* — at 900 the pane crossed most of
      // its travel inside two frames while a 130 ms fade was still at full alpha, and what the eye
      // caught was a modal that vanished with its text and a scrim left standing alone. The exit
      // has to be one gesture: the pane shrinks back onto the row it came from *while* it fades,
      // and the scrim, which reads the same fade, leaves with it.
      launch { fade.animateTo(0f, FluidMotion.fadeOut(FluidPopoverFadeOutMillis)) }
      launch { scaleX.animateTo(0f, spring(1f, FluidPopoverExitStiffness, 0.001f)) }
      launch { scaleY.animateTo(0f, spring(1f, FluidPopoverExitStiffness, 0.001f)) }
    }
  }

  // Read through a lambda by whoever *draws*, never as a composed value. `fade.value` read here
  // subscribed this whole layer — scrim, lifted row, popover, its `Layout` — to every frame of
  // every arrival, and recomposing means re-measuring the modal's content: a paragraph-heavy
  // detail re-laid-out sixty times a second is precisely the stutter an entrance cannot have.
  // Composition needs exactly one coarse fact, and it is the boolean below.
  val presence: () -> Float = { fade.value }

  // `derivedStateOf` recomputes on every animation frame but invalidates composition only when
  // the boolean flips — once per exit, instead of once per frame.
  //
  // **Solo il foglio aspetta le molle.** Per tutto il resto la verita' e' la dissolvenza, e
  // aspettare la coda della molla di scala era un difetto vero: l'opacita' arrivava a zero a 240 ms
  // e il layer restava fino a circa 590, con la riga ancorata ancora nascosta per tutto quel tempo.
  // Quello che si vedeva era un buco al posto della riga per un terzo di secondo, e poi la riga che
  // ricompariva di colpo. Il foglio invece *e'* la sua molla: la sua opacita' dipende da quanto ha
  // ancora da viaggiare, quindi li' la coda va aspettata.
  val waitsForSprings = entry.presentation == FluidGlassModalPresentation.Sheet
  val exitFinished by remember(waitsForSprings) {
    derivedStateOf {
      fade.value <= 0.001f && !fade.isRunning && (!waitsForSprings || !scaleY.isRunning)
    }
  }

  // Everything the exit still needs is held from the last frame the modal was open. These are
  // *values* — an actions list, a rectangle, a title — and values survive a dismissal. What CANNOT
  // be preserved this way is the content composable: a composable lambda is one mutable holder per
  // call site whose block the compiler swaps in place, so the reference kept here IS the fresh
  // lambda, captures and all. What keeps the pane full during the exit is therefore the *data*
  // overload of [FluidGlassModalPortal], which freezes the last shown item and hands it back to the
  // very same lambda.
  var lastContent by remember { mutableStateOf(entry.content) }
  var lastActions by remember { mutableStateOf(entry.actions) }
  var lastPreview by remember { mutableStateOf(entry.preview) }
  var lastPreviewBounds by remember { mutableStateOf(entry.previewSize) }
  var lastOrigin by remember { mutableStateOf<Rect?>(null) }
  var lastPaneTitle by remember { mutableStateOf(entry.paneTitle) }
  if (entry.visible) {
    lastContent = entry.content
    lastActions = entry.actions
    lastPreview = entry.preview
    lastPreviewBounds = entry.previewSize
    lastOrigin = entry.origin()
    lastPaneTitle = entry.paneTitle
    // Not only a context menu lifts its row out of the page: anything that morphs out of an anchor
    // does, because the pane is glass and the anchor would otherwise be legible through it.
    entry.lifted = true
  }

  // L'ancora VERA — la riga registrata da fluidExpandOrigin i cui limiti coincidono con l'origine.
  // Trovarla e' cio' che trasforma "un pannello compare dov'era la riga" in "la riga diventa il
  // pannello": la riga smette di disegnarsi nello stesso fotogramma in cui la finestra appare
  // sulla sua sagoma, e la sua immagine registrata parte col viaggio.
  var anchorNode by remember { mutableStateOf<FluidExpandAnchorNode?>(null) }
  if (entry.visible && journeyMode && entry.presentation != FluidGlassModalPresentation.ContextMenu) {
    val origin = lastOrigin
    if (origin != null && anchorNode == null) {
      anchorNode = host.expandAnchors.firstOrNull { node ->
        (node.bounds.center - origin.center).getDistance() < 4f &&
          abs(node.bounds.width - origin.width) < 4f &&
          abs(node.bounds.height - origin.height) < 4f
      }
    }
    anchorNode?.hidden = true
  }
  DisposableEffect(entry) {
    onDispose { anchorNode?.hidden = false }
  }

  // The fade is short and the springs are not, and a sheet slides out on the springs. Dropping
  // the layer the moment the alpha reached zero cut every exit in half.
  if (!entry.visible && exitFinished) {
    // Whatever was lifted out of the page is handed back to it here, and not one frame earlier.
    SideEffect {
      entry.lifted = false
      anchorNode?.hidden = false
      anchorNode = null
    }
    return
  }

  val focusRequester = remember { FocusRequester() }
  LaunchedEffect(entry.visible) {
    if (entry.visible) runCatching { focusRequester.requestFocus() }
  }

  // Predictive back drives the same retreat the drag does, so letting go halfway puts the modal
  // back where it was instead of jumping.
  var backProgress by remember { mutableStateOf(0f) }
  PredictiveBackHandler(enabled = entry.visible) { events ->
    try {
      events.collect { backProgress = it.progress.coerceIn(0f, 1f) }
      backProgress = 0f
      entry.onDismissRequest()
    } catch (_: CancellationException) {
      backProgress = 0f
    }
  }

  val scrimGlass = rememberGlassBackdrop()
  // The pop-up refracts the page *as the scrim left it* — dimmed and lightly frosted — not the raw
  // page. Handing it the page directly punches a clear hole straight through the scrim, which is
  // exactly what it looks like.
  val popoverBackdrop = if (backdrop != null) {
    rememberCombinedGlassBackdrop(backdrop, scrimGlass)
  } else {
    null
  }

  Box(
    modifier = Modifier
      .fillMaxSize()
      .semantics { isTraversalGroup = true }
      .focusGroup(),
  ) {
    FluidGlassModalScrim(
      backdrop = backdrop,
      exports = scrimGlass,
      intensity = { presence() * (1f - backProgress * 0.6f) },
      // A congedo richiesto lo scrim smette di mangiare i tocchi: la pagina sotto e' gia' sua
      // — si puo' scorrere mentre la chiusura finisce di sfumare.
      interactive = entry.visible,
      onDismiss = entry.onDismissRequest,
    )

    // An invisible focus target inside the overlay: without one, the first D-pad or keyboard move
    // after opening lands somewhere in the page underneath — the same failure the accessibility
    // rule on [fluidGlassModalObscured] is about.
    Box(
      modifier = Modifier
        .size(1.dp)
        .focusRequester(focusRequester)
        .focusable(),
    )

    if (entry.presentation == FluidGlassModalPresentation.Sheet) {
      FluidGlassModalSheet(
        paneTitle = lastPaneTitle,
        backdrop = popoverBackdrop,
        // Sheets arrive by *travelling*, not by fading: the spring is the animation, and the alpha
        // only exists so the last few pixels of the exit do not pop.
        slide = { scaleY.value },
        backProgress = { backProgress },
        reducedMotion = reducedMotion,
        onDismiss = entry.onDismissRequest,
        content = lastContent,
      )
      return@Box
    }

    val menu = entry.presentation == FluidGlassModalPresentation.ContextMenu
    val expand = entry.presentation == FluidGlassModalPresentation.Expand
    if (menu) {
      FluidLiftedPreview(
        preview = lastPreview,
        bounds = lastPreviewBounds,
        backdrop = popoverBackdrop,
        // Never faded. The real row hides the moment the menu opens and comes back only at
        // [exitFinished] — the preview is the row for that whole stretch, and fading it out on the
        // menu's clock opened a two-hundred-millisecond hole where the group pane showed through
        // with no text in it. At both ends of its life the preview sits at scale 1 exactly over the
        // row it recorded, so appearing and vanishing are pixel-identical swaps, not cuts.
        presence = { 1f },
        // The row is already its own size, so it only has the lift to travel: a few percent, on the
        // same springs, so the row and the menu move as one object.
        scaleX = { lerp(1f, FluidLiftedPreviewScale, scaleX.value) * (1f - backProgress * 0.05f) },
        scaleY = { lerp(1f, FluidLiftedPreviewScale, scaleY.value) * (1f - backProgress * 0.05f) },
      )
    }

    val anchorRadiusPx = with(LocalDensity.current) {
      val origin = lastOrigin
      if (expand && origin != null) {
        // Il tasto Expand e' una capsula: la finestra parte dalla SUA sagoma, non da un rettangolo
        // qualsiasi — e' l'intera differenza fra "il tasto diventa il pannello" e "un pannello
        // compare dov'era il tasto".
        origin.height / 2f
      } else {
        FluidRadius.Control.toPx()
      }
    }
    FluidAnchoredPopover(
      anchor = lastOrigin,
      paneTitle = lastPaneTitle ?: if (menu || expand) "Azioni" else null,
      backdrop = popoverBackdrop,
      compact = menu || expand,
      // Tutto quello che nasce da un'ancora si apre **sopra di lei**, tranne il menu contestuale:
      // quello ha la riga sollevata li' accanto, e crescere sopra la riga significherebbe coprire
      // proprio l'oggetto che il sollevamento sta presentando.
      overAnchor = !menu,
      morphWindow = journeyMode,
      anchorRadiusPx = anchorRadiusPx,
      outgoing = anchorNode?.layer,
      windowBackdrop = backdrop,
      open = { entry.visible },
      presence = presence,
      growth = { scaleX.value },
      growthCross = { scaleY.value },
      retreat = { backProgress },
    ) {
      if (menu || expand) {
        lastActions.forEachIndexed { index, action ->
          if (index > 0) FluidHairline(startInset = 14.dp)
          FluidContextMenuRow(action = action, onDismiss = entry.onDismissRequest)
        }
      } else {
        lastContent()
      }
    }
  }
}

/**
 * The scrim.
 *
 * Almost nothing, and that is the decision. The temptation with a full-screen pane is to turn the
 * radius up until the page behind is unrecognisable — which does separate the pop-up, at the price
 * of throwing away the only reason to keep the modal inside the app's composition at all. A page
 * averaged into grey is not "visible behind": it is grey, and a painted rectangle would have got
 * there cheaper. So the frosting stays at a fraction of a bar's, the page keeps its colour, and the
 * separation is bought with a light drop in brightness plus the pop-up's own edge and shadow.
 *
 * Displacement is zero on purpose: a lens the size of the display bends the corners of the screen
 * and reads as a fisheye rather than as depth.
 */
@Composable
private fun FluidGlassModalScrim(
  backdrop: GlassBackdropState?,
  exports: GlassBackdropState,
  intensity: () -> Float,
  interactive: Boolean,
  onDismiss: () -> Unit,
) {
  val dark = GlassDefaults.isDarkSurface()
  val tint = remember(dark) {
    GlassTint(
      overlay = Color.Black.copy(alpha = if (dark) 0.20f else 0.12f),
      // Below API 31 there is no blur at all, so the scrim stops pretending and becomes a wall. The
      // modal works identically; you simply cannot see through it.
      fallback = Color.Black.copy(alpha = 0.92f),
      hairline = Color.Transparent,
    )
  }
  val optics = remember {
    GlassDefaults.optics(GlassRole.Modal).copy(
      // Two dp times two. Enough that the page reads as *behind* something rather than as
      // merely darker, and far too little to average it away.
      blurScale = 1.5f,
      refractionHeight = 0.dp,
      refractionAmount = 0.dp,
      vibrancy = 1f,
      highlightAlpha = 0f,
      innerShadowAlpha = 0f,
      shadowAlpha = 0f,
    )
  }

  val base = Modifier
    .fillMaxSize()
    // The arrival is an alpha on the finished pane, **not** the material's own `intensity`.
    //
    // `intensity` scales the blur radius, and a changing blur radius changes the padding the layer
    // needs, and a changing padding is the one thing that genuinely invalidates a capture. Ramping
    // it therefore re-records the whole screen and re-runs the entire effect chain on every frame of
    // the fade — on the largest surface in the system, at the exact moment something is trying to
    // animate. Fading the result instead costs one composited layer and looks identical.
    .graphicsLayer { alpha = intensity().coerceIn(0f, 1f) }
    // Il cancello del fotogramma di confine: al primo e all'ultimo fotogramma di vita del nodo
    // il layer qui sopra puo' disegnare con l'alpha di default (1) — e' il lampo scuro+blur di
    // un fotogramma misurato nel video dell'apertura E della chiusura. Con l'alpha a zero non
    // si registra niente, e una displaylist vuota non ha lampi da rigiocare.
    .drawWithContent { if (intensity() > 0.004f) drawContent() }
    .then(
      if (interactive) {
        Modifier.pointerInput(onDismiss) { detectTapGestures { onDismiss() } }
      } else {
        Modifier
      },
    )

  if (backdrop == null) {
    Box(
      modifier = base.drawWithContent {
        drawRect(tint.fallback)
        drawContent()
      },
    )
  } else {
    Box(
      modifier = base.glassSurface(
        state = backdrop,
        tint = tint,
        role = GlassRole.Modal,
        optics = optics,
        exports = exports,
      ),
    )
  }
}

/**
 * Where the pop-up ended up, and the rectangle it grew out of.
 *
 * Written by the measure pass and read by the draw pass. Kept in one object rather than in
 * separate states because the start geometry and the position are one decision: split them and
 * there is a frame where the pop-up is already in its new place and still growing out of its old
 * rectangle.
 */
@Stable
private class FluidPopoverPlacement {
  var pivotX by mutableStateOf(0.5f)
  var pivotY by mutableStateOf(0.5f)

  /**
   * How small the pop-up starts, per axis, as a fraction of its finished size.
   *
   * Solved from the anchor rather than fixed, because "grows out of the button" is a statement about
   * two rectangles and not about a number: a wide button opening a narrow menu has to start wide,
   * and a constant makes that read as a zoom from somewhere else.
   */
  var startScaleX by mutableStateOf(FluidPopoverStartScale)
  var startScaleY by mutableStateOf(FluidPopoverStartScale)

  /**
   * Where the pop-up's top-left corner sits at the start of its travel, relative to where it ends —
   * which, morphing from an anchor, is the anchor's own corner. Zero for the pivot-based growth
   * a context menu keeps.
   */
  var startOffsetX by mutableStateOf(0f)
  var startOffsetY by mutableStateOf(0f)

  /** True when the pop-up starts as the anchor's exact rectangle and travels to its place. */
  var morphFromAnchor by mutableStateOf(false)

  /** The anchor's rectangle in the pane's own coordinates: the frame the pane grows out of. */
  var anchorLeft by mutableStateOf(0f)
  var anchorTop by mutableStateOf(0f)
  var anchorWidth by mutableStateOf(0f)
  var anchorHeight by mutableStateOf(0f)

  /** La taglia risolta del pannello: serve a ombra e immagine in uscita, che vivono fuori dal Layout. */
  var paneWidth by mutableStateOf(0f)
  var paneHeight by mutableStateOf(0f)
}

/**
 * The pane's frame while it opens: the anchor's rectangle, travelling to the pane's own.
 *
 * This has to be a **frame** and not a scale, and the distinction is the whole history of this
 * animation. Scaling the pane from the anchor's rectangle means two different factors on the two
 * axes — a card three hundred dp tall squeezed into a row of ninety while keeping its width —
 * and for the first third of every opening the type was anamorphic. Nothing in an interface is
 * anamorphic, so the eye catches it at once.
 *
 * A travelling frame has no factors at all. What grows is the shape the pane is seen through; the
 * pane inside it is scaled only *uniformly*, by a few percent, so it reads as coming toward you
 * without one letter changing proportion.
 *
 * The outline is a plain rounded rectangle rather than this system's continuous corner, and
 * deliberately: a generic path is CPU-rasterised into Skia's small-path atlas keyed by subpixel
 * phase, so a corner that moves every frame would re-rasterise every frame. The smoothing lives
 * inside two pixels of a corner that is in motion.
 */
private class FluidPopoverMorphWindow(
  private val placement: FluidPopoverPlacement,
  private val growth: () -> Float,
  private val startRadiusPx: Float,
  private val endRadiusPx: Float,
) : Shape {
  override fun createOutline(
    size: Size,
    layoutDirection: LayoutDirection,
    density: Density,
  ): Outline {
    val g = growth().coerceIn(0f, 1f)
    val left = lerp(placement.anchorLeft, 0f, g)
    val top = lerp(placement.anchorTop, 0f, g)
    val right = lerp(placement.anchorLeft + placement.anchorWidth, size.width, g)
    val bottom = lerp(placement.anchorTop + placement.anchorHeight, size.height, g)
    val radius = lerp(startRadiusPx, endRadiusPx, g)
    return Outline.Rounded(
      RoundRect(
        rect = Rect(left, top, maxOf(right, left + 1f), maxOf(bottom, top + 1f)),
        cornerRadius = CornerRadius(radius, radius),
      ),
    )
  }
}


/**
 * A pop-up placed against [anchor], sized to its content, growing out of the corner nearest it.
 *
 * A `Layout` rather than a measured offset, because the alternative flashes: anything that has to
 * read its own size out of state before it can place itself gets drawn once in the wrong place
 * first, and on something that arrives under the finger, once is enough to see.
 *
 * The size is the content's own. That is what "dynamic" means here, and it is the reason this is not
 * simply a dialog with a source rectangle: a three-line menu is three lines tall, a detail with a
 * paragraph in it is as tall as the paragraph, and neither is padded out to a card.
 */
@Composable
private fun FluidAnchoredPopover(
  anchor: Rect?,
  paneTitle: String?,
  backdrop: GlassBackdropState?,
  compact: Boolean,
  /** True for [FluidGlassModalPresentation.Expand]: sit *on* the anchor rather than beside it. */
  overAnchor: Boolean,
  /**
   * Fluid Engine, 1.9.5: la finestra e' di Fluid-physics. La sagoma viaggia dal rettangolo
   * dell'ancora a quella del pannello con la rifrazione addosso — non un pannello scalato, la
   * silhouette stessa che si trasforma. L'orologio resta quello del modale (molle, dissolvenze,
   * back predittivo): la fisica e' guidata dall'esterno, vedi [FluidPhysicsState.driveExternally].
   */
  morphWindow: Boolean,
  anchorRadiusPx: Float,
  /**
   * L'immagine registrata della riga che ha aperto la finestra, da [fluidExpandOrigin]. Viaggia
   * con la silhouette e svanisce nel primo terzo del percorso: e' la meta' "in uscita" del
   * contratto dei contenuti — la riga non sparisce, PARTE.
   */
  outgoing: GraphicsLayer?,
  /** La pagina cruda, per la finestra morph: il composito con lo scrim mente (vedi sotto). */
  windowBackdrop: GlassBackdropState?,
  /** Vero finche' il modale e' aperto: la chiusura cambia il clock del materiale della finestra. */
  open: () -> Boolean,
  presence: () -> Float,
  growth: () -> Float,
  growthCross: () -> Float,
  retreat: () -> Float,
  content: @Composable ColumnScope.() -> Unit,
) {
  val density = LocalDensity.current
  val gapPx = with(density) { FluidPopoverGap.toPx() }
  val marginPx = with(density) { FluidPopoverMargin.toPx() }
  val maxWidthPx = with(density) {
    when {
      compact -> FluidContextMenuMaxWidth
      // Un pop-up che nasce da una riga NON e' largo quanto la riga: se lo fosse, il viaggio
      // sarebbe solo verticale — un allungamento, non un'apertura. Stretto come una card, il
      // morph guadagna la componente laterale (i fianchi della riga si raccolgono) e il
      // pannello si legge come un oggetto, non come una fascia.
      anchor != null -> FluidPopoverAnchoredMaxWidth
      else -> FluidPopoverMaxWidth
    }.toPx()
  }
  val minWidthPx = with(density) { if (compact) FluidContextMenuMinWidth.toPx() else 0f }
  val paneRadiusPx = with(density) { (if (compact) FluidRadius.Group else FluidPopoverRadius).toPx() }
  val placement = remember { FluidPopoverPlacement() }
  val shape = ContinuousCornerShape(if (compact) FluidRadius.Group else FluidPopoverRadius)
  // Il pannello registra se stesso, cosi' quello che ci sta dentro puo' rifrangere *lui* invece
  // della pagina, che sta dietro uno scrim e non e' affar suo.
  val paneGlass = rememberGlassBackdrop()
  val windowPhysics = remember { FluidPhysicsState(FluidForm.circle(Offset(1f, 1f), 1f)) }
  val useMorphWindow = morphWindow && anchor != null && backdrop != null
  // La pellicola della finestra con dentro la quota di scrim: lo stesso nero dello scrim vero,
  // composto sotto la tinta flottante. E' il punto di partenza del lerp tintFrom->tint guidato
  // da (1 - presence): a modale fermo la finestra porta lo scrim intero, lungo le dissolvenze
  // esattamente la frazione che lo scrim ha sullo schermo.
  val windowDark = GlassDefaults.isDarkSurface()
  val windowFloatingTint = GlassDefaults.floatingTint()
  val windowTintWithScrim = remember(windowFloatingTint, windowDark) {
    windowFloatingTint.copy(
      overlay = windowFloatingTint.overlay.compositeOver(
        Color.Black.copy(alpha = if (windowDark) 0.20f else 0.12f),
      ),
    )
  }

  if (useMorphWindow && backdrop != null) {
    val shadowBlurPx = with(density) { FluidPopoverOptics.shadowRadius.toPx() }
    val shadowDropPx = with(density) { FluidPopoverShadowDrop.toPx() }
    val shadowCache = remember { FluidPopoverShadowCache() }
    // Il materiale della finestra: la dissolvenza del modale, e in CHIUSURA anche il viaggio —
    // pellicola e ombra muoiono mentre la sagoma atterra sulla riga, non dopo. In apertura il
    // viaggio non c'entra: il materiale e' costante, cosi' quello che si vede attraverso il
    // vetro non pulsa ("il testo dietro non deve muoversi").
    val windowAmount: () -> Float = {
      presence() * (if (open()) 1f else minOf(growth(), growthCross()).coerceIn(0f, 1f))
    }
    // La finestra: una superficie a tutto schermo la cui silhouette e' il lerp ancora->pannello
    // al passo delle molle del modale. Il clip del layer resta fermo, la sagoma la scolpisce lo
    // shader — e' il contratto di Fluid-physics, quindi questo non ricattura e non ri-clippa mai.
    //
    // NIENTE scala sul layer: scalare il nodo scala anche il fondale campionato dentro il vetro
    // — "il testo dietro si muove", ed era vero. L'oltrepasso vive nella GEOMETRIA della
    // silhouette (overshootInflation di driveExternally): un moto solo, su tutti i lati.
    Box(
      modifier = Modifier
        .fillMaxSize()
        .drawWithContent {
          // Il cancello del fotogramma di confine: al primo e all'ultimo fotogramma di vita del
          // nodo, il layer esterno puo' disegnare con le proprieta' di default — un lampo a
          // piena intensita', misurato alla luminanza nel video (214.6 -> 190.9 -> 204.1: un
          // fotogramma di scrim pieno, poi la dissolvenza che riparte da zero). Se il materiale
          // e' a zero non si disegna NIENTE, e una displaylist vuota non ha lampi da rigiocare.
          if (windowAmount() > 0.004f) drawContent()
        }
        .drawBehind {
          // L'ombra della finestra: senza, il pannello aperto galleggiava piatto. La prima
          // versione passava un BlurMaskFilter sul path della silhouette A OGNI FOTOGRAMMA, e
          // HWUI ri-rasterizza la maschera ogni volta che il path cambia — era il lag che si
          // vedeva a occhio nudo. Qui la sfocatura si paga una volta sola, in un bitmap per
          // taglia di pannello, e ogni fotogramma e' un drawImage stirato sui bounds correnti:
          // un'ombra morbida stirata e una esatta sono indistinguibili.
          // Muore CON l'atterraggio, non col proprio orologio: un'ombra ancora accesa attorno a
          // una sagoma gia' posata sulla riga e' "il tasto resta scurito fino all'ultimo frame".
          val amount = windowAmount()
          if (amount > 0.02f) {
            val bounds = windowPhysics.ensurePlan().bounds
            val shadow = shadowCache.obtain(
              paneWidth = placement.paneWidth,
              paneHeight = placement.paneHeight,
              cornerRadius = paneRadiusPx,
              blur = shadowBlurPx,
            )
            if (shadow != null && bounds.width > 1f && bounds.height > 1f) {
              val pad = shadowBlurPx * 2f
              val sx = bounds.width / placement.paneWidth
              val sy = bounds.height / placement.paneHeight
              drawImage(
                image = shadow,
                dstOffset = IntOffset(
                  (bounds.left - pad * sx).roundToInt(),
                  (bounds.top - pad * sy + shadowDropPx).roundToInt(),
                ),
                dstSize = IntSize(
                  (bounds.width + pad * 2f * sx).roundToInt().coerceAtLeast(1),
                  (bounds.height + pad * 2f * sy).roundToInt().coerceAtLeast(1),
                ),
                alpha = (FluidPopoverOptics.shadowAlpha * amount).coerceIn(0f, 1f),
              )
            }
          }
        }
        .fluidPhysicsSurface(
          state = windowPhysics,
          // La pagina CRUDA, non il composito pagina+scrim. Gli exports dello scrim registrano
          // il suo materiale a forza PIENA (l'arrivo dello scrim e' un alpha sul pannello
          // finito), quindi campionare il composito significava mostrare dentro la card uno
          // scrim sempre pieno mentre quello sullo schermo sfumava — la card restava SCURA per
          // tutta la chiusura, misurato sui fotogrammi. Lo scurimento dello scrim la finestra
          // se lo dipinge da sola, qui sotto, alla forza CORRENTE.
          backdrop = windowBackdrop ?: backdrop,
          // Piu' leggero del modale classico, deliberato: "troppo glassoso" e' stato il verdetto
          // sulla prima versione. Pellicola da capsula flottante — il vetro lo fa la rifrazione
          // al bordo, non il latte — ma il blur non scende sotto la leggibilita', e il bordo e'
          // PRONUNCIATO: e' il bordo a dire "abbiamo selezionato questo", come sul menu
          // contestuale.
          tint = GlassDefaults.floatingTint(),
          role = GlassRole.Modal,
          optics = FluidPopoverOptics.copy(
            blurScale = 2.2f,
            highlightWidth = 1.1.dp,
            highlightAlpha = 0.62f,
          ),
          // La pellicola porta anche la quota di scrim che spetta alla finestra, alla forza a
          // cui lo scrim sta DAVVERO in questo fotogramma: piena a modale aperto, a scalare
          // lungo le dissolvenze. Cosi' il vetro non e' mai ne' piu' scuro ne' piu' chiaro
          // della pagina scrimmata che lo circonda.
          tintFrom = windowTintWithScrim,
          tintBlend = { 1f - presence().coerceIn(0f, 1f) },
          intensity = windowAmount,
        ),
    )
    // L'immagine della riga, in viaggio: parte dalla sua posizione esatta, segue il centro della
    // silhouette e svanisce nel primo terzo — la riga vera ha smesso di disegnarsi nello stesso
    // fotogramma, quindi quello che l'occhio segue e' UNA cosa che si trasforma.
    if (outgoing != null && anchor != null) {
      Box(
        modifier = Modifier
          .offset { IntOffset(anchor.left.roundToInt(), anchor.top.roundToInt()) }
          .size(
            width = with(density) { anchor.width.toDp() },
            height = with(density) { anchor.height.toDp() },
          )
          .graphicsLayer {
            val g = growth()
            val gc = growthCross()
            val travel = minOf(g, gc)
            // MAI moltiplicata per la dissolvenza del modale: al primo fotogramma la riga vera
            // e' gia' nascosta e la dissolvenza e' a zero — con `presence` qui dentro il vetro
            // partiva VUOTO. E in chiusura e' questa immagine, riapparendo lungo il ritorno, a
            // coprire lo scambio con la riga vera: quando il layer si smonta sono gia' identiche.
            // Vive fino a meta' viaggio: e' il titolo della riga che TRASLA dentro la card,
            // mentre il testo della card subentra sotto — e all'inverso in chiusura.
            alpha = (1f - travel / 0.5f).coerceIn(0f, 1f)
            val paneCenterX = anchor.left - placement.anchorLeft + placement.paneWidth / 2f
            val paneCenterY = anchor.top - placement.anchorTop + placement.paneHeight / 2f
            translationX = g.coerceAtMost(1f) * (paneCenterX - anchor.center.x)
            translationY = gc.coerceAtMost(1f) * (paneCenterY - anchor.center.y)
            val shrink = 1f - 0.06f * travel.coerceIn(0f, 1f)
            scaleX = shrink
            scaleY = shrink
          }
          .drawWithContent {
            // Un layer rilasciato non si ridisegna: se la lista sotto ha smontato la riga, la
            // finestra apre semplicemente senza immagine in uscita. Niente qui vale un crash.
            runCatching { drawLayer(outgoing) }
          },
      )
    }
  }

  Layout(
    modifier = Modifier.fillMaxSize(),
    content = {
      Column(
        modifier = Modifier
          .graphicsLayer {
            val g = growth()
            val gc = growthCross()
            val shrink = 1f - retreat() * 0.10f
            if (useMorphWindow) {
              // Il contenuto viaggia col contratto del menu che si espande — fluidPhysicsContent,
              // la ricetta gia' approvata — non con uno suo: TRASLA col centro corrente della
              // silhouette, si scala UNIFORME sulla taglia vera (min-dimension, con gli stessi
              // limiti 0.72..1.12), e la scritta si materializza nella seconda meta' del viaggio.
              // La versione precedente teneva il testo a taglia piena dall'inizio: sbordava dal
              // vetro ancora piccolo, ed era "il testo che appare in maniera strana".
              val travel = minOf(g, gc)
              // Subentra TARDI (45%..90% del viaggio): prima e' l'immagine della riga a viaggiare
              // — il titolo che trasla dentro la card — e solo poi si materializza il testo,
              // sotto. La staffetta delle due scritte e' cio' che toglie il popping del testo.
              alpha = presence() * ((travel - 0.45f) / 0.45f).coerceIn(0f, 1f)
              val anchorCenterX = placement.anchorLeft + placement.anchorWidth / 2f
              val anchorCenterY = placement.anchorTop + placement.anchorHeight / 2f
              translationX = (1f - g.coerceAtMost(1f)) * (anchorCenterX - size.width / 2f)
              translationY = (1f - gc.coerceAtMost(1f)) * (anchorCenterY - size.height / 2f)
              val endMin = minOf(size.width, size.height)
              // Zoom di pochi punti percentuali, e NIENTE gonfiore del pop sul testo: la
              // trasformazione la racconta gia' l'immagine della riga in viaggio, e un
              // paragrafo che zooma da 0.72 gonfiando oltre l'1 era "il testo esplode".
              // Il pop resta al vetro; la scritta arriva in posa.
              val travelScale = if (endMin > 0f) {
                val startMin = minOf(placement.anchorWidth, placement.anchorHeight)
                (lerp(startMin, endMin, travel.coerceIn(0f, 1f)) / endMin).coerceIn(0.90f, 1f)
              } else {
                1f
              }
              scaleX = travelScale * shrink
              scaleY = travelScale * shrink
              transformOrigin = TransformOrigin(0.5f, 0.5f)
            } else {
              // L'opacita' cavalca la corsa, non l'orologio: vicino all'ancora — i primi istanti
              // di un'apertura, gli ultimi di una chiusura — il pannello e' trasparente, quindi
              // quando se ne va ha finito di dissolversi *prima* di arrivare sulla riga in cui
              // rientra, e le due cose non sono mai leggibili una sopra l'altra.
              alpha = presence() * ((minOf(g, gc) - 0.08f) / 0.27f).coerceIn(0f, 1f)
              scaleX = lerp(placement.startScaleX, 1f, g) * shrink
              scaleY = lerp(placement.startScaleY, 1f, gc) * shrink
              transformOrigin = TransformOrigin(placement.pivotX, placement.pivotY)
            }
          }
          .then(
            if (backdrop != null && !useMorphWindow) {
              Modifier.glassSurface(
                state = backdrop,
                tint = GlassDefaults.modalTint(),
                shape = shape,
                role = GlassRole.Modal,
                optics = FluidPopoverOptics,
                // Captured once and carried. The pane spends both of its journeys being scaled and
                // translated by the layer above, and a re-capture computed mid-transform maps the
                // sources wrongly — which on every close stripped the pane of its glass and left a
                // milky card travelling down the screen. The recording's sources stay live by
                // reference, so what is frozen is only the geometry.
                sampleOnce = true,
                exports = paneGlass,
              )
            } else if (backdrop == null) {
              Modifier.background(MaterialTheme.colorScheme.surfaceContainerHigh, shape)
            } else {
              Modifier
            },
          )
          .clip(shape)
          .semantics {
            paneTitle?.let { this.paneTitle = it }
            isTraversalGroup = true
          },
      ) {
        // **Il contenuto viaggia con la finestra, non compare dentro di lei.**
        //
        // Comparire era il difetto: il testo era gia' alla sua posizione finale e si accendeva mano
        // a mano che il bordo della finestra ci passava sopra, cioe' esattamente l'effetto di una
        // tendina tirata su un cartello gia' scritto. Non e' un'apertura, e' una rivelazione.
        //
        // Traslando dello stesso vettore della finestra, quello che si vede attraverso il buco resta
        // *sempre lo stesso pezzo di contenuto*: al primo fotogramma il titolo sta dove stava il
        // titolo della riga, e da li' scivola al suo posto insieme al bordo che lo scopre. In
        // chiusura la stessa corsa all'indietro riporta il titolo dentro la riga. E' il motivo per
        // cui l'apertura di un'app su iOS si legge come una cosa sola che si avvicina invece che
        // come due cose che si scambiano il posto.
        Column(
          modifier = Modifier.graphicsLayer {
            if (!placement.morphFromAnchor) return@graphicsLayer
            // Uniforme, e di pochi punti percentuali. Serve perche' il contenuto *cresca* col bordo
            // invece di stare fermo mentre una tendina lo scopre — ma con un fattore solo per i due
            // assi, che e' l'unica cosa che il testo non perdona.
            val zoom = lerp(FluidPopoverContentStartScale, 1f, growth())
            scaleX = zoom
            scaleY = zoom
            transformOrigin = TransformOrigin(
              if (size.width > 0f) {
                ((placement.anchorLeft + placement.anchorWidth / 2f) / size.width).coerceIn(0f, 1f)
              } else {
                0.5f
              },
              if (size.height > 0f) {
                ((placement.anchorTop + placement.anchorHeight / 2f) / size.height).coerceIn(0f, 1f)
              } else {
                0.5f
              },
            )
          },
        ) {
          CompositionLocalProvider(
            LocalFluidCanvasBackdrop provides paneGlass,
            LocalFluidCanvasIsGlass provides true,
          ) {
            content()
          }
        }
      }
    },
  ) { measurables, constraints ->
    val width = constraints.maxWidth
    val height = constraints.maxHeight
    val available = (width - marginPx * 2f).coerceAtLeast(1f)
    val maxContentWidth = minOf(maxWidthPx, available).roundToInt().coerceAtLeast(1)

    val placeable = measurables.first().measure(
      constraints.copy(
        minWidth = minWidthPx.roundToInt().coerceIn(0, maxContentWidth),
        minHeight = 0,
        maxWidth = maxContentWidth,
        maxHeight = (height * FluidPopoverMaxHeightFraction - marginPx * 2f)
          .roundToInt()
          .coerceAtLeast(1),
      ),
    )

    if (anchor == null) {
      placement.pivotX = 0.5f
      placement.pivotY = 0.5f
      placement.startScaleX = FluidPopoverStartScale
      placement.startScaleY = FluidPopoverStartScale
      placement.morphFromAnchor = false
      return@Layout layout(width, height) {
        placeable.place(
          x = ((width - placeable.width) / 2f).roundToInt(),
          y = ((height - placeable.height) / 2f).roundToInt(),
        )
      }
    }

    val spaceBelow = height - anchor.bottom - gapPx - marginPx
    val spaceAbove = anchor.top - gapPx - marginPx
    val below = placeable.height <= spaceBelow || spaceBelow >= spaceAbove

    val maxX = (width - placeable.width - marginPx).coerceAtLeast(marginPx)
    // Centred on what was touched, then clamped to the screen. A pop-up pinned to the leading edge
    // of a full-width row looks like it belongs to the row text; pinned to its centre it looks like
    // it belongs to the touch.
    val x = (anchor.center.x - placeable.width / 2f).coerceIn(marginPx, maxX)
    val y = if (overAnchor) {
      // Sitting *on* the anchor: the edge the menu grows away from stays put, so the button does
      // not appear to jump the moment it unfolds.
      if (below) {
        anchor.top.coerceAtMost((height - placeable.height - marginPx).coerceAtLeast(marginPx))
      } else {
        (anchor.bottom - placeable.height).coerceAtLeast(marginPx)
      }
    } else if (below) {
      (anchor.bottom + gapPx)
        .coerceAtMost((height - placeable.height - marginPx).coerceAtLeast(marginPx))
    } else {
      (anchor.top - gapPx - placeable.height).coerceAtLeast(marginPx)
    }

    // The pivot is the point on the pop-up closest to what was touched, expressed as a fraction of
    // its own box. Growing from there is the difference between a pop-up that opens *out of* the
    // anchor and one that merely happens to be next to it.
    placement.pivotX = if (placeable.width <= 0) {
      0.5f
    } else {
      ((anchor.center.x - x) / placeable.width).coerceIn(0f, 1f)
    }
    placement.pivotY = if (below) 0f else 1f

    // **Una crescita sola, per ogni pop-up.**
    //
    // Ce n'erano due, e la seconda — quella che partiva dal rettangolo esatto dell'ancora — e'
    // stata due volte un errore. Scalando i due assi con fattori diversi schiacciava il testo; e
    // quando l'ho sostituita con una finestra che si apre, il contenuto ci compariva dentro mano a
    // mano che il bordo ci passava sopra, che e' una tendina tirata su un cartello gia' scritto.
    //
    // Quella che restava — la crescita del menu contestuale — non ha nessuno dei due problemi,
    // perche' e' **uniforme**. La tiene il menu; tutto il resto, che si apre *sopra* la propria
    // ancora, torna a partire dal rettangolo di quell'ancora — ma facendo viaggiare il **bordo**
    // invece di scalare il pannello, che era la ragione per cui il testo si schiacciava.
    placement.morphFromAnchor = overAnchor && placeable.width > 0 && placeable.height > 0
    placement.anchorLeft = anchor.left - x
    placement.anchorTop = anchor.top - y
    placement.anchorWidth = anchor.width
    placement.anchorHeight = anchor.height
    placement.paneWidth = placeable.width.toFloat()
    placement.paneHeight = placeable.height.toFloat()
    placement.startScaleX = FluidPopoverStartScale
    placement.startScaleY = FluidPopoverStartScale
    placement.startOffsetX = 0f
    placement.startOffsetY = 0f

    if (useMorphWindow && placeable.width > 0 && placeable.height > 0) {
      // La geometria della finestra si risolve QUI, dove si risolve tutto il resto: l'ancora com'e'
      // davvero, il pannello dov'e' davvero. L'orologio e' `growth` — le molle del modale.
      // La partenza e' il rettangolo ESATTO dell'ancora: partire ribassato dava una spinta verso
      // l'alto all'andata, ma al ritorno la finestra atterrava quattordici punti sotto la riga —
      // "non ritorna al punto originale", ed e' vero. La vita verticale la danno gia' le molle.
      windowPhysics.driveExternally(
        from = FluidForm.Slab(
          frame = anchor,
          cornerRadii = FluidCornerRadii.all(
            anchorRadiusPx.coerceAtMost(anchor.minDimension / 2f),
          ),
        ),
        to = FluidForm.Slab(
          frame = Rect(x, y, x + placeable.width, y + placeable.height),
          cornerRadii = FluidCornerRadii.all(paneRadiusPx),
        ),
        progress = growth,
        // Oltre l'1 la sagoma NON prosegue lungo il lerp (fianchi in dentro, fondo in fuori —
        // la direzione del viaggio, non un pop): e' il pannello gonfiato uniformemente attorno
        // al centro, su tutti i lati, che poi si posa. Un moto solo, nella geometria.
        overshootInflation = FluidPopoverWindowPopGain,
      )
    }

    layout(width, height) { placeable.place(x.roundToInt(), y.roundToInt()) }
  }
}

/**
 * The row, lifted out of the page and turned into a pane of the menu's own material.
 *
 * Not a scaled copy of itself. The first version simply replayed the row's recording a little
 * larger, and what that reads as is the row *doubling* — there are suddenly two of it, one blurred
 * and one not, and neither has become anything. What actually happens on iOS is that the thing you
 * held **turns into** the same glass card the menu is made of, and keeps its own contents printed on
 * it. So the recording is drawn onto a real [GlassRole.Modal] surface with the menu's tint and the
 * menu's radius, and the pair grows out of the touch on the same two springs.
 *
 * The recording is what makes this one modifier rather than a second copy of the row's content:
 * whatever the row draws comes along, whatever it happens to be.
 */
@Composable
private fun FluidLiftedPreview(
  preview: GraphicsLayer?,
  bounds: Rect?,
  backdrop: GlassBackdropState?,
  presence: () -> Float,
  scaleX: () -> Float,
  scaleY: () -> Float,
) {
  if (preview == null || bounds == null) return
  val density = LocalDensity.current
  val shape = ContinuousCornerShape(FluidPopoverRadius)
  Box(
    modifier = Modifier
      .offset { IntOffset(bounds.left.roundToInt(), bounds.top.roundToInt()) }
      .size(
        width = with(density) { bounds.width.toDp() },
        height = with(density) { bounds.height.toDp() },
      )
      .graphicsLayer {
        // **Opaca sempre, finche' esiste.**
        //
        // Sfumare la copia sollevata voleva dire far sparire la riga: quella vera sotto e' nascosta
        // fino a che il livello non viene smontato, quindi una copia che si dissolve lascia un
        // rettangolo vuoto dove c'era la riga. Quello che deve dissolversi non e' l'immagine, e' il
        // *materiale*: sotto, `intensity` porta a zero vetro, bordo e tinta, e quando arriva a zero
        // questa copia e' identica alla riga — cosi' lo scambio, che avviene nello stesso istante,
        // non ha niente da mostrare.
        this.scaleX = scaleX()
        this.scaleY = scaleY()
        // Grown from its own bottom edge, because that is the edge the menu appears against: the
        // row and the menu below it then open as one object rather than as two things that both
        // happen to be arriving.
        transformOrigin = TransformOrigin(0.5f, 1f)
      }
      .then(
        if (backdrop != null) {
          Modifier.glassSurface(
            state = backdrop,
            tint = GlassDefaults.modalTint(),
            shape = shape,
            role = GlassRole.Modal,
            optics = FluidPopoverOptics,
            // Il materiale entra ed esce; l'immagine no. E' questa la transizione che mancava alla
            // chiusura: il colore torna quello di prima e il bordo si spegne, invece di essere
            // scambiati di colpo con la riga vera.
            intensity = { presence() },
          )
        } else {
          Modifier.background(
            MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = presence()),
            shape,
          )
        },
      )
      .clip(shape)
      .drawWithContent {
        // Replaying a released layer is a crash, and the list underneath can dispose a row while
        // the menu is up. Nothing here is worth a crash: if the recording is gone, the pane simply
        // comes up empty and the menu keeps its actions.
        runCatching { drawLayer(preview) }
      },
  )
}

@Composable
private fun FluidContextMenuRow(
  action: FluidContextAction,
  onDismiss: () -> Unit,
) {
  val color = if (action.destructive) {
    MaterialTheme.colorScheme.error
  } else {
    MaterialTheme.colorScheme.onSurface
  }
  Row(
    modifier = Modifier
      .fillMaxWidth()
      .heightIn(min = 48.dp)
      .fluidRowPressable(
        onClick = {
          // A disabled action closing the menu would be indistinguishable from having performed it.
          if (!action.enabled) return@fluidRowPressable
          onDismiss()
          action.onClick()
        },
        enabled = action.enabled,
      )
      .padding(horizontal = 16.dp, vertical = 12.dp),
    horizontalArrangement = Arrangement.spacedBy(12.dp),
    verticalAlignment = Alignment.CenterVertically,
  ) {
    CompositionLocalProvider(LocalContentColor provides color) {
      Text(
        text = action.label,
        modifier = Modifier
          .weight(1f)
          .graphicsLayer { alpha = if (action.enabled) 1f else 0.38f },
        style = MaterialTheme.typography.bodyLarge,
        fontWeight = FontWeight.Medium,
        color = color,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
      )
      action.icon?.let {
        Icon(
          imageVector = it,
          contentDescription = null,
          modifier = Modifier
            .size(20.dp)
            .graphicsLayer { alpha = if (action.enabled) 1f else 0.38f },
          tint = color,
        )
      }
    }
  }
}

/**
 * The bottom-anchored variant, for content tall enough to want the whole height.
 *
 * The only presentation whose edge is against the screen rather than against the thing that opened
 * it, and therefore the only one that behaves like a sheet: it travels in from below on a spring,
 * and it can be **pushed back out with a finger, from anywhere on it**.
 *
 * "From anywhere" is the part that takes work, because a sheet is allowed to contain a list and a
 * downward drag is then ambiguous. It is resolved the way Material resolves it, with nested scroll:
 * the content gets the gesture first and keeps it for as long as it has somewhere to scroll; the
 * moment it runs out — [NestedScrollConnection.onPostScroll] reporting leftover downward travel —
 * the sheet takes the remainder and starts moving. Letting go hands the fling to whichever of the
 * two is holding the gesture. A sheet with nothing scrollable in it never reaches that negotiation
 * and is simply dragged.
 */
@Composable
private fun FluidGlassModalSheet(
  paneTitle: String?,
  backdrop: GlassBackdropState?,
  slide: () -> Float,
  backProgress: () -> Float,
  reducedMotion: Boolean,
  onDismiss: () -> Unit,
  content: @Composable ColumnScope.() -> Unit,
) {
  val shape = ContinuousCornerShape(topStart = FluidRadius.Sheet, topEnd = FluidRadius.Sheet)
  val density = LocalDensity.current
  val scope = rememberCoroutineScope()
  val dismissThresholdPx = with(density) { FluidSheetDismissTravel.toPx() }
  val flingDismissPx = with(density) { FluidSheetDismissVelocity.toPx() }

  // Plain state, not an `Animatable`: nested-scroll callbacks are not suspending, and a drag has to
  // land on the same frame as the finger. The animatable only comes out to settle it.
  var dragPx by remember { mutableFloatStateOf(0f) }
  var height by remember { mutableFloatStateOf(0f) }

  fun settle(velocity: Float) {
    val shouldDismiss = dragPx > dismissThresholdPx || velocity > flingDismissPx
    if (shouldDismiss) {
      onDismiss()
      return
    }
    scope.launch {
      val from = dragPx
      androidx.compose.animation.core.animate(
        initialValue = from,
        targetValue = 0f,
        initialVelocity = velocity,
        animationSpec = spring(0.9f, 500f, 0.5f),
      ) { value, _ -> dragPx = value }
    }
  }

  val nested = remember(dismissThresholdPx, flingDismissPx) {
    object : NestedScrollConnection {
      override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset {
        // Pulling back up while the sheet is already out of place closes that gap first, before the
        // content is allowed to scroll. Otherwise a half-dragged sheet scrolls its list instead of
        // returning, which reads as the drag having been ignored.
        if (source != NestedScrollSource.UserInput || available.y >= 0f || dragPx <= 0f) {
          return Offset.Zero
        }
        val used = (-available.y).coerceAtMost(dragPx)
        dragPx -= used
        return Offset(0f, -used)
      }

      override fun onPostScroll(
        consumed: Offset,
        available: Offset,
        source: NestedScrollSource,
      ): Offset {
        if (source != NestedScrollSource.UserInput || available.y <= 0f) return Offset.Zero
        dragPx += available.y
        return Offset(0f, available.y)
      }

      override suspend fun onPreFling(available: Velocity): Velocity {
        if (dragPx <= 0f) return Velocity.Zero
        settle(available.y)
        return available
      }
    }
  }

  Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.BottomCenter) {
    Column(
      modifier = Modifier
        .fillMaxWidth()
        .heightIn(max = FluidSheetMaxHeight)
        .onSizeChanged { height = it.height.toFloat() }
        .nestedScroll(nested)
        .draggable(
          orientation = Orientation.Vertical,
          state = rememberDraggableState { delta -> dragPx = (dragPx + delta).coerceAtLeast(0f) },
          onDragStopped = { velocity -> settle(velocity) },
        )
        .graphicsLayer {
          val travelled = slide().coerceIn(0f, 1f)
          // Below the screen at 0, home at 1, and the drag is added on top in raw pixels so it
          // tracks the finger exactly rather than through an eased curve.
          translationY = if (reducedMotion) {
            dragPx
          } else {
            size.height * (1f - travelled) + dragPx
          }
          // The alpha only covers the last sliver of the exit; the travel is the animation.
          alpha = (travelled * 4f).coerceIn(0f, 1f)
          val retreat = 1f - backProgress() * 0.06f
          scaleX = retreat
          scaleY = retreat
          transformOrigin = TransformOrigin(0.5f, 1f)
        }
        .then(
          if (backdrop != null) {
            Modifier.glassSurface(
              state = backdrop,
              tint = GlassDefaults.modalTint(),
              shape = shape,
              role = GlassRole.Modal,
              optics = FluidPopoverOptics,
            )
          } else {
            Modifier.background(MaterialTheme.colorScheme.surfaceContainerHigh, shape)
          },
        )
        .clip(shape)
        .semantics {
          paneTitle?.let { this.paneTitle = it }
          isTraversalGroup = true
        },
    ) {
      FluidGrabber()
      content()
    }
  }
}

// ---------------------------------------------------------------------------------------------
// Context menu
// ---------------------------------------------------------------------------------------------

/**
 * Long-press this element to raise a menu of actions anchored to it.
 *
 * The sequence is the iOS one, and every step of it is doing a job: the haptic confirms the press
 * before anything moves, the page falls behind a lightly frosted scrim so the menu has somewhere to
 * be, the row itself is redrawn *in its own place* and lifted, and the actions grow out of the
 * corner nearest it.
 *
 * The lifted row is not the same instance — that one is still in the list, under the scrim. It is a
 * **recording**: the element paints itself into a `GraphicsLayer` and the host replays it. Recording
 * costs nothing while nobody is touching the row, because it only starts on the finger going down
 * and stops when it comes back up.
 *
 * Below API 31 the menu keeps working with opaque surfaces. It loses the blur, not the function.
 */
/**
 * A context menu that something else decides when to open.
 *
 * Two pieces instead of one, and the split is not tidiness — it is the only arrangement that works.
 * A `detectTapGestures { onLongPress }` sitting next to a row's own `combinedClickable` never fires:
 * the click handler consumes the gesture first and the long press is simply eaten, silently, which
 * is exactly what happened the first time this shipped. Anything that already handles clicks has to
 * raise the menu from **its own** long-press callback, and that is what [open] is for.
 *
 * [Modifier.fluidContextMenu] is the convenience form for elements that do *not* handle clicks: it
 * pairs this with a gesture of its own.
 */
@Stable
class FluidContextMenuController internal constructor(
  private val host: FluidGlassModalHostState?,
  internal val entry: FluidGlassModalEntry,
  internal val layer: GraphicsLayer,
) {
  internal var actionsProvider: () -> List<FluidContextAction> = { emptyList() }

  /** Whether the element is currently painting itself into [layer] for the overlay to replay. */
  internal var recording by mutableStateOf(false)

  val isOpen: Boolean get() = entry.visible

  /** Starts capturing the element's picture. Called on touch-down, long before a menu can open. */
  internal fun arm(pressed: Boolean) {
    recording = pressed || entry.visible
    if (recording) entry.preview = layer
  }

  /** Raises the menu. Returns false when there is nothing to show, so the caller can fall through. */
  fun open(): Boolean {
    val resolvedHost = host ?: return false
    val list = actionsProvider()
    if (list.isEmpty()) return false
    entry.actions = list
    entry.preview = layer
    entry.openedAt = resolvedHost.nextSequence()
    entry.visible = true
    entry.lifted = true
    recording = true
    return true
  }

  fun dismiss() {
    entry.visible = false
    recording = false
  }
}

/**
 * The state behind a context menu, so a component that owns its own gestures can raise one.
 *
 * Pair it with [Modifier.fluidContextMenuAnchor] on the element the menu belongs to, and call
 * [FluidContextMenuController.open] from that element's long-press.
 */
@Composable
fun rememberFluidContextMenu(
  actions: () -> List<FluidContextAction>,
): FluidContextMenuController {
  val host = LocalFluidGlassModalHostState.current
  val entry = remember { FluidGlassModalEntry() }
  val layer = rememberGraphicsLayer()
  val controller = remember(host, entry, layer) {
    FluidContextMenuController(host, entry, layer)
  }

  SideEffect {
    controller.actionsProvider = actions
    entry.presentation = FluidGlassModalPresentation.ContextMenu
    entry.onDismissRequest = controller::dismiss
  }

  DisposableEffect(host, entry) {
    host?.register(entry)
    onDispose {
      // The recording belongs to this node and dies with it. Clearing the host's reference first is
      // what stops the overlay from replaying a layer the runtime has already released.
      entry.preview = null
      entry.visible = false
      host?.unregister(entry)
    }
  }

  return controller
}

/**
 * Marks the element a [FluidContextMenuController] belongs to.
 *
 * Reports where it is, and paints it into a `GraphicsLayer` while a finger is on it so the overlay
 * has a finished picture to lift. The press is observed on the **initial** pass and nothing is
 * consumed, so this can sit under a row that handles its own clicks without taking anything away
 * from it — which is the entire reason the capture is separate from the gesture.
 *
 * Recording only runs between touch-down and touch-up, so a list of these costs nothing at rest.
 */
@Composable
fun Modifier.fluidContextMenuAnchor(controller: FluidContextMenuController): Modifier {
  val scope = rememberCoroutineScope()
  val reducedMotion = LocalFluidMotionPolicy.current.reducedMotion
  // The same specular hotspot the glass controls carry, for the same reason: a row that is about to
  // become a pane of glass has to answer the finger like one *before* it does. Without it a long
  // press is a blank half-second and then a whole overlay, with nothing in between to say the press
  // was even received — and half a second of nothing is how long it takes to decide a control
  // is broken and lift your finger off it.
  val highlight = remember(scope, reducedMotion) {
    GlassTouchHighlight(
      animationScope = scope,
      strength = { if (reducedMotion) 0f else 1f },
    )
  }
  return this
    .onGloballyPositioned {
      val rect = it.boundsInRoot()
      controller.entry.previewSize = rect
      controller.entry.origin = { rect }
    }
    .pointerInput(controller) {
      awaitPointerEventScope {
        while (true) {
          val event = awaitPointerEvent(PointerEventPass.Initial)
          controller.arm(event.changes.any { it.pressed })
        }
      }
    }
    .graphicsLayer {
      if (reducedMotion) return@graphicsLayer
      // Swells under the finger, the way the navigation pill does, and settles back when it is let
      // go. Rows normally must not scale — shrinking one row of a stack breaks the group's
      // silhouette — but this one is on its way *out* of the stack, and growing says so.
      val swell = 1f + FluidContextAnchorSwell * highlight.pressProgress
      scaleX = swell
      scaleY = swell
    }
    // Ahead of the capture, not inside it. The trail belongs to the finger and the capture is a
    // still: record the glow too and the lifted pane arrives with a bright smear frozen onto it,
    // sitting wherever the finger happened to be on the frame the press was recognised.
    .then(highlight.modifier)
    .then(highlight.gestureModifier)
    .drawWithContent {
      // Lifted: the row is *in the overlay now*, and drawing it here as well is what made the
      // menu look broken. The copy in front is a pane of glass, so the row still sitting in the
      // page was being refracted through it and arriving a second time, blurred and a few pixels
      // off — every line of it doubled behind itself. The space stays reserved, because this is a
      // draw and not a layout, so nothing below it moves while the menu is up.
      if (controller.entry.lifted) return@drawWithContent
      if (controller.recording) {
        controller.layer.record { this@drawWithContent.drawContent() }
        drawLayer(controller.layer)
      } else {
        drawContent()
      }
    }
}

/** How much a row swells while it is being held. A few percent: it is a row, not a card yet. */
private const val FluidContextAnchorSwell = 0.02f

/**
 * Long-press this element to raise a menu of actions anchored to it.
 *
 * The sequence is the iOS one, and every step of it is doing a job: the haptic confirms the press
 * before anything moves, the page falls behind a lightly frosted scrim so the menu has somewhere to
 * be, the row itself is redrawn *in its own place* and lifted, and the actions grow out of the
 * corner nearest it.
 *
 * The lifted element is not the same instance — that one is still in the list, under the scrim. It
 * is a **recording**, replayed one level up.
 *
 * Use this only on something that does **not** already handle clicks. On anything that does — a
 * list row, a card with an `onClick` — the click handler consumes the gesture and this never fires;
 * use [rememberFluidContextMenu] with [fluidContextMenuAnchor] and open the menu from that
 * element's own long-press instead. [dev.antigravity.fluidengine.ui.theme.FluidListRow] does exactly
 * that behind its `contextActions` parameter.
 *
 * Below API 31 the menu keeps working with opaque surfaces. It loses the blur, not the function.
 */
@Composable
fun Modifier.fluidContextMenu(
  actions: () -> List<FluidContextAction>,
  enabled: Boolean = true,
): Modifier {
  val controller = rememberFluidContextMenu(actions)
  val haptics = LocalHapticFeedback.current
  if (!enabled) return this
  return this
    .fluidContextMenuAnchor(controller)
    .pointerInput(controller) {
      detectTapGestures(
        onLongPress = {
          if (controller.open()) haptics.performHapticFeedback(HapticFeedbackType.LongPress)
        },
      )
    }
}

/**
 * The pop-up's own material.
 *
 * Real glass, with the lens on. A modal that is merely translucent is the failure this file exists
 * to avoid: now that the scrim barely frosts the page, a flat wash over it would show the page
 * straight through, unbent, and read as a semi-transparent rectangle rather than as a pane. The blur
 * stays modest and the *displacement* does the identifying — the page visibly bending into the
 * pop-up's perimeter is the whole tell that there is a thickness there.
 */
private val FluidPopoverOptics = GlassOptics(
  // The one surface here that *is* frosted, and the division of labour is deliberate: the scrim
  // barely touches the page, the pop-up frosts hard. That is the way round Apple does it — the page
  // stays itself and merely dims, and the thing in front of it is the pane you cannot read through.
  // The reverse (a heavy scrim under a clear pop-up) hides the page and gains nothing.
  //
  // This was twelve dp, on top of a scrim that had already blurred the same pixels, and the result
  // was the complaint that started this pass: a pop-up you cannot see anything through is not
  // glass, it is a light grey card, and every one of the expensive things below — the bevel, the
  // rim, the vibrancy — was being spent describing the edge of a blank. The material is the
  // *bend*; frosting is only what keeps the text on top readable, and five dp over an
  // already-dimmed page is enough for that.
  blurScale = 2.5f,
  // And the bevel comes in to match. Twenty-eight dp of displacement on a menu 210 dp wide meant a
  // quarter of the pane was edge: what should read as a thick slab read as a fisheye, with the page
  // smeared round the outside of it in a coloured halo.
  refractionHeight = 14.dp,
  refractionAmount = 18.dp,
  depthEffect = false,
  dispersion = false,
  vibrancy = 1.6f,
  highlightWidth = 0.8.dp,
  // Bright enough to find the edge, dim enough that the pane is not outlined in white. At 0.7 the
  // rim was the loudest thing on the screen and the pop-up read as a sticker.
  highlightAlpha = 0.45f,
  highlightAngle = 90f,
  // Nessuna ombra interna: il rim speculare e l'ombra interna sono due trattamenti dello stesso
  // millimetro di perimetro, e insieme si leggono come due bordi invece che come spessore.
  innerShadowRadius = 0.dp,
  innerShadowAlpha = 0f,
  // Tight and soft. A wide dark shadow over a scrim that is already darkening the page reads as
  // grime around the pop-up rather than as height.
  shadowRadius = 18.dp,
  shadowAlpha = 0.36f,
  pressedDepthBoost = 0f,
)

/**
 * How small the pop-up starts.
 *
 * Not zero. Growing from nothing is a zoom, and a zoom has no material in it — the pop-up has to be
 * recognisably itself from the first frame and simply be *small*, the way the tab bar's pill is
 * always a pill however hard it is squashed.
 */
/**
 * Il moto della finestra ancorata, detto parola per parola: "accelerazione continua finche' non
 * raggiunge il bordo, e allora inizia a rallentare, oltre, fino a ritornare alla posizione
 * finale". Quindi: una rincorsa TUTTA in ease-in che copre il novanta per cento del viaggio
 * ancora in accelerazione (130ms, curva che finisce ripida), e una molla che ne eredita la
 * velocita' — l'oltrepasso e' slancio vero, non un calcio dato alla fine. La prima rincorsa
 * (180ms all'80%, easing molle) era stata bocciata come "lineare, lenta": il difetto era il
 * ritmo, non l'idea.
 */
private const val FluidPopoverWindowRunUpMillis = 130
private const val FluidPopoverWindowRunUpEnd = 0.92f
private val FluidPopoverWindowRunUpEasing = CubicBezierEasing(0.55f, 0f, 0.85f, 0.55f)
private const val FluidPopoverWindowDampingX = 0.55f
private const val FluidPopoverWindowDampingY = 0.62f
private const val FluidPopoverWindowStiffness = 300f

/**
 * Quanto dell'oltrepasso della molla diventa GONFIORE della card (via overshootInflation di
 * driveExternally): geometria, non scala del layer — scalare il layer zoomava anche il fondale
 * campionato dentro il vetro, "il testo dietro si muove". Uniforme, su tutti i lati.
 */
private const val FluidPopoverWindowPopGain = 0.6f

/**
 * L'uscita della finestra: piu' rigida di quella classica, perche' la sagoma deve ATTERRARE
 * sulla riga prima che la dissolvenza (280ms, sotto) finisca — e' l'ordine delle due cose che
 * rende la chiusura un gesto solo invece di una sparizione per aria.
 */
private const val FluidPopoverJourneyExitStiffness = 620f
private const val FluidPopoverJourneyFadeOutMillis = 280

private const val FluidPopoverStartScale = 0.42f

/** How much the lifted row grows. Any more and it stops being the row and becomes a card. */
private const val FluidLiftedPreviewScale = 1.03f

/**
 * Across and down. Different on purpose: two matched springs give a balloon, two mismatched ones
 * give the squash that makes this read as the same object as the navigation pill.
 */
private const val FluidPopoverDampingX = 0.62f
private const val FluidPopoverDampingY = 0.74f
private const val FluidPopoverStiffness = 260f

private const val FluidPopoverFadeInMillis = 110

/**
 * L'uscita dura piu' dell'ingresso, ed e' voluto: all'ingresso il contenuto deve essere leggibile
 * il prima possibile, all'uscita l'occhio sta seguendo il pannello che *torna nella riga*, e un
 * fade che finisce prima della corsa lascia uno scrim orfano sopra una pagina senza modale.
 */
private const val FluidPopoverFadeOutMillis = 240
private const val FluidPopoverExitStiffness = 420f

/** Quanto e' piccolo il contenuto quando il bordo comincia ad aprirsi. Pochi punti percentuali. */
private const val FluidPopoverContentStartScale = 0.90f

private val FluidPopoverMargin = 16.dp
private val FluidPopoverGap = 10.dp
// Largo abbastanza da far respirare un dettaglio con allegati su un tablet; su un telefono e' la
// larghezza dello schermo meno i margini a decidere, quindi il numero grande non cambia niente li'.
private val FluidPopoverMaxWidth = 620.dp

/**
 * Il pop-up ANCORATO e' una card, non una fascia: un po' piu' stretto della riga da cui nasce,
 * cosi' il morph ha una componente laterale — ma di POCO. A 320 la strizzata dominava e il
 * pannello sembrava rimpicciolirsi invece di crescere; la crescita la deve raccontare l'altezza.
 */
private val FluidPopoverAnchoredMaxWidth = 360.dp

/** Di quanto l'ombra della finestra sta sotto la silhouette: il pannello e' sospeso, non incollato. */
private val FluidPopoverShadowDrop = 6.dp

/**
 * L'ombra della finestra, sfocata UNA volta per taglia di pannello.
 *
 * Il BlurMaskFilter passato sul path della silhouette a ogni fotogramma era il lag dell'apertura:
 * HWUI rasterizza la maschera daccapo ogni volta che il path cambia, e in un morph il path cambia
 * sempre. Qui il blur si paga alla prima richiesta, in un bitmap a mezza risoluzione (l'ombra e'
 * morbida per definizione: mezza risoluzione non si vede), e il fotogramma costa un drawImage.
 */
private class FluidPopoverShadowCache {
  private var image: ImageBitmap? = null
  private var keyWidth = -1f
  private var keyHeight = -1f
  private var keyRadius = -1f
  private var keyBlur = -1f

  fun obtain(paneWidth: Float, paneHeight: Float, cornerRadius: Float, blur: Float): ImageBitmap? {
    if (paneWidth < 1f || paneHeight < 1f || blur < 1f) return null
    if (image == null || paneWidth != keyWidth || paneHeight != keyHeight ||
      cornerRadius != keyRadius || blur != keyBlur
    ) {
      keyWidth = paneWidth
      keyHeight = paneHeight
      keyRadius = cornerRadius
      keyBlur = blur
      val pad = blur * 2f
      val scale = 0.5f
      val bitmap = android.graphics.Bitmap.createBitmap(
        (((paneWidth + pad * 2f) * scale).toInt()).coerceAtLeast(2),
        (((paneHeight + pad * 2f) * scale).toInt()).coerceAtLeast(2),
        android.graphics.Bitmap.Config.ARGB_8888,
      )
      val canvas = android.graphics.Canvas(bitmap)
      val paint = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
        color = android.graphics.Color.BLACK
        maskFilter = android.graphics.BlurMaskFilter(
          (blur * scale).coerceAtLeast(1f),
          android.graphics.BlurMaskFilter.Blur.NORMAL,
        )
      }
      canvas.drawRoundRect(
        pad * scale,
        pad * scale,
        (pad + paneWidth) * scale,
        (pad + paneHeight) * scale,
        cornerRadius * scale,
        cornerRadius * scale,
        paint,
      )
      image = bitmap.asImageBitmap()
    }
    return image
  }
}
private const val FluidPopoverMaxHeightFraction = 0.78f
/**
 * The pop-up's corner, and it is [FluidRadius.Group] on purpose.
 *
 * A pop-up that grows out of a row is growing out of a *grouped list*, and it starts at that row's
 * rectangle: if its radius were its own number, the first frame would be the row with the wrong
 * corners and the animation would be spent correcting them. Sharing the group's radius means the
 * corner the pop-up starts with is the corner it was standing on.
 */
private val FluidPopoverRadius = FluidRadius.Group

private val FluidSheetMaxHeight = 640.dp

/** How far the sheet must be pushed down before letting go dismisses instead of springing back. */
private val FluidSheetDismissTravel = 110.dp

/** Or how fast, in dp per second: a short flick down closes it however little it travelled. */
private val FluidSheetDismissVelocity = 900.dp

private val FluidContextMenuMinWidth = 210.dp
private val FluidContextMenuMaxWidth = 300.dp
