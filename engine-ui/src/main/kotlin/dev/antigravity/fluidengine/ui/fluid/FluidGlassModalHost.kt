package dev.antigravity.fluidengine.ui.fluid

import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.IntRect
import androidx.activity.compose.PredictiveBackHandler
import androidx.compose.animation.core.Animatable
import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.focusable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
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
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.layer.GraphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.PointerEvent
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.PointerId
import androidx.compose.ui.node.ModifierNodeElement
import androidx.compose.ui.node.PointerInputModifierNode
import androidx.compose.ui.platform.InspectorInfo
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.isTraversalGroup
import androidx.compose.ui.semantics.hideFromAccessibility
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.IntSize
import dev.antigravity.fluidengine.ui.glass.field.GlassPaintedScrim
import kotlinx.coroutines.launch
import kotlin.coroutines.cancellation.CancellationException
import kotlin.math.abs
import kotlin.math.roundToInt

// Il lato della radice di un modale: lo stato dell'host, la voce che ogni modale vi registra, l'host che li
// disegna e i layer per famiglia (avviso, menu, pannello di contenuto). Prima di F5A.0 stava tutto in
// `FluidGlassModal.kt`. Chi dichiara un modale sta in `FluidGlassModalPortal.kt`; quello che il pannello
// disegna ha ognuno il suo file: `FluidGlassModalPopover`, `FluidGlassModalSheet`, `FluidGlassModalScrim`,
// `FluidLiftedPreview` e `FluidContextMenu`.

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

  /**
   * Chi vuole sapere di un tocco FUORI da una zona dello schermo (3.0): il menu che si trasforma,
   * quando il suo host non e' a schermo intero. L'host di quel menu prende i tocchi solo dentro i
   * propri limiti — montato in un palco, un tocco sopra il palco non lo chiudeva — e l'unico posto
   * che vede tutto lo schermo e' questo, alla radice. Qui si disegnano quattro fasce attorno alla
   * zona: un tocco su una fascia chiude, dentro la zona i tocchi restano dell'host.
   */
  internal val outsideCatchers = mutableStateListOf<FluidOutsideTapCatcher>()

  private var sequence = 0

  /** Whether anything is currently open. An app can use it to obscure the page behind. */
  val isPresenting: Boolean get() = entries.any { it.visible }

  internal val blocksPage: Boolean get() = entries.any { it.visible && it.blocksPage }

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

  /**
   * Il chiamante se n'e' andato, ma l'uscita deve vedersi (3.0, per [FluidAlert] nell'host): la voce
   * resta registrata, si chiude, e la toglie [finishExit] quando il layer ha finito di uscire. Con
   * `unregister` l'avviso tolto dalla composizione spariva di colpo, testo e vetro insieme, nel
   * fotogramma stesso in cui il chiamante l'aveva tolto.
   */
  internal fun retire(entry: FluidGlassModalEntry) {
    if (entries.none { it === entry }) return
    entry.retiring = true
    entry.visible = false
  }

  /** L'uscita di una voce ritirata e' finita: ora la si toglie davvero. Non tocca le altre. */
  internal fun finishExit(entry: FluidGlassModalEntry) {
    if (entry.retiring) unregister(entry)
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

/**
 * Una zona dello schermo ([hole], pixel della radice) fuori dalla quale un tocco chiama [onTap]: vedi
 * [FluidGlassModalHostState.outsideCatchers].
 */
@Stable
internal class FluidOutsideTapCatcher(val onTap: () -> Unit) {
  var hole by mutableStateOf(Rect.Zero)
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

@Stable
internal class FluidGlassModalEntry {
  var visible by mutableStateOf(false)
  var openedAt by mutableIntStateOf(0)
  var onDismissRequest: () -> Unit by mutableStateOf({})
  var origin: () -> Rect? by mutableStateOf({ null })
  var presentation by mutableStateOf(FluidGlassModalPresentation.Popover)
  var source: FluidTransitionSource? by mutableStateOf(null)
  var mode: FluidPresentationMode? by mutableStateOf(null)
  var inlineConfirmation by mutableStateOf(false)
  val sheetBackground = FluidSheetBackground()
  val outsideTapArea = FluidModalHitArea()
  val blocksPage: Boolean get() =
    mode != FluidPresentationMode.Parallel && !inlineConfirmation && !sheetBackground.undimmed
  var paneTitle: String? by mutableStateOf(null)
  var paneTint: Color? by mutableStateOf(null)

  /** Il fondo di una pagina intera, al posto di `surface` (3.0.1). Null: quello del tema. */
  var pageColor: Color? by mutableStateOf(null)
  var content: (@Composable ColumnScope.() -> Unit) by mutableStateOf({})

  /** I tasti in fondo a una pagina intera, fermi mentre il contenuto scorre. Null: non ce ne sono. */
  var footer: (@Composable ColumnScope.() -> Unit)? by mutableStateOf(null)

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

  /**
   * Il tasto da cui un menu `Expand` nasce (3.0): la sua sagoma premuta, il suo materiale, la sua
   * faccia e il cancello che lo nasconde. Null per il menu contestuale, che cresce da un seme.
   */
  var menuAnchor: FluidMenuAnchor? by mutableStateOf(null)

  /** La scelta del menu di questa voce: il pannello le scrive la geometria, l'ancora le passa il dito. */
  val selection = FluidMenuSelection()

  /**
   * Il ponte del dito di chi ha aperto il menu (l'ancora o la riga): il layer lo scollega al congedo, cosi'
   * un dito ancora giu' dopo un back non tiene ferma la lista attorno.
   */
  var menuHandOff: FluidMenuHandOff? = null

  /** Un [FluidAlert] nell'host: titolo, messaggio, pulsanti e contenuto. Null per tutto il resto. */
  var alert: FluidAlertSpec? by mutableStateOf(null)

  /** Ritirata con [FluidGlassModalHostState.retire]: il layer la toglie a uscita finita. */
  var retiring: Boolean by mutableStateOf(false)
}

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
  if (!host.blocksPage) return this
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
  if (state.entries.isEmpty() && state.outsideCatchers.isEmpty()) return
  // Chi disegna il pannello dichiara il colore di quello che ci sta sopra (3.0.1), come
  // `FluidSheetScaffold` e `FluidScreen`. L'host sta alla radice, quasi sempre fuori da una `Surface`,
  // e li' `LocalContentColor` vale nero: un `Text` senza colore in un pop-up, un foglio, una pagina
  // intera o un avviso era nero sul tema scuro (il selettore dei formati di universal_converter).
  // `onSurface` e' il testo sul vetro dei pannelli e sulla pagina intera, che e' `surface`.
  // Taglia, stile e larghezza dei tasti tornano quelli di serie (F6.4): in iOS 27 un foglio o un popover non eredita
  // `controlSize` e `buttonSizing` di chi lo apre, e un host messo dentro una barra di tasti `Compact` non li passa.
  CompositionLocalProvider(LocalContentColor provides MaterialTheme.colorScheme.onSurface, *fluidGlassButtonDefaults()) {
    val parallel = state.topEntry()?.takeIf { !it.blocksPage && !fluidModalSlidesUp(it.presentation) }
    val sharedTap = if (parallel != null) {
      val slop = androidx.compose.ui.platform.LocalViewConfiguration.current.touchSlop
      FluidParallelModalTapElement(parallel.outsideTapArea, slop, parallel.onDismissRequest)
    } else Modifier
    Box(modifier = modifier.fillMaxSize().then(sharedTap)) {
      // Every registered entry, in the order they were opened, so a menu raised from inside a modal
      // sits over it instead of replacing it. An entry that is closed and finished animating draws
      // nothing at all and costs one float animation.
      state.entries.sortedBy { it.openedAt }.forEach { entry ->
        key(entry) { FluidGlassModalLayer(entry = entry, backdrop = backdrop, host = state) }
      }
      // Sopra tutto: un menu che si trasforma aperto in un palco si chiude toccando ovunque fuori.
      state.outsideCatchers.forEach { catcher ->
        key(catcher) { FluidOutsideTapStrips(catcher) }
      }
    }
  }
}

/**
 * Le quattro fasce attorno al buco di [catcher]: invisibili, prendono un tocco e chiamano chi le ha
 * chieste. Dentro il buco non c'e' niente, quindi i tocchi arrivano a cio' che sta sotto (il menu e
 * il suo host). Un trascinamento che parte su una fascia non scorre la pagina: con un menu aperto la
 * pagina e' ferma, come quando l'host e' a schermo intero.
 */
@Composable
private fun FluidOutsideTapStrips(catcher: FluidOutsideTapCatcher) {
  Layout(
    modifier = Modifier.fillMaxSize(),
    content = {
      repeat(4) {
        Box(modifier = Modifier.pointerInput(catcher) { detectTapGestures(onTap = { catcher.onTap() }) })
      }
    },
  ) { measurables, constraints ->
    val w = constraints.maxWidth
    val h = constraints.maxHeight
    val hole = catcher.hole
    val l = hole.left.roundToInt().coerceIn(0, w)
    val t = hole.top.roundToInt().coerceIn(0, h)
    val r = hole.right.roundToInt().coerceIn(l, w)
    val b = hole.bottom.roundToInt().coerceIn(t, h)
    // In alto, in basso, a sinistra, a destra.
    val boxes = listOf(
      IntRect(0, 0, w, t),
      IntRect(0, b, w, h),
      IntRect(0, t, l, b),
      IntRect(r, t, w, b),
    )
    val placeables = measurables.mapIndexed { i, m ->
      m.measure(Constraints.fixed(boxes[i].width.coerceAtLeast(0), boxes[i].height.coerceAtLeast(0)))
    }
    layout(w, h) {
      placeables.forEachIndexed { i, p -> p.place(boxes[i].left, boxes[i].top) }
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
  // Tre famiglie, un host (3.0, fase 7): l'avviso, i menu (contestuale ed Expand con azioni: il
  // pannello unico, il viaggio unico, la goccia) e i pannelli di contenuto di sempre.
  val alert = entry.alert
  val hasActions = fluidModalShowsActions(entry.presentation, entry.actions.isNotEmpty())
  val sourceJourney = rememberFluidModalSourceJourney(
    entry, enabled = !hasActions && !fluidModalSlidesUp(entry.presentation) &&
      entry.presentation != FluidGlassModalPresentation.ContextMenu,
    motion = when {
      alert?.confirmationCancel != null -> FluidModalSourceMotion.Confirmation
      alert != null -> FluidModalSourceMotion.Alert
      else -> FluidModalSourceMotion.Popover
    },
  )
  when {
    alert != null -> FluidAlertLayer(entry, alert, backdrop, host, sourceJourney)
    hasActions ->
      FluidMenuLayer(entry = entry, backdrop = backdrop)
    else -> FluidGlassModalPaneLayer(entry, backdrop, host, sourceJourney)
  }
}

@Composable
private fun FluidGlassModalPaneLayer(
  entry: FluidGlassModalEntry,
  backdrop: GlassBackdropState?,
  host: FluidGlassModalHostState,
  sourceJourney: FluidModalSourceJourney?,
) {
  val reducedMotion = LocalFluidMotionPolicy.current.reducedMotion
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
    !fluidModalSlidesUp(entry.presentation)

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
      launch { fade.animateTo(1f, FluidMotion.fadeIn(FluidMotion.PopoverFadeInMillis)) }
      if (journeyMode && entry.origin() != null) {
        // Nessun colpo qui (3.0): un momento, un evento, e il momento e' il tocco sulla riga, che il
        // suo colpo ce l'ha gia'. Il layer che suonava Open si sommava a quello di chi apriva.
        // La formula, detta parola per parola: ACCELERAZIONE CONTINUA finche' non raggiunge il
        // bordo, poi oltre, frena, e torna alla posizione finale. Quindi: una rincorsa tutta in
        // ease-in che copre quasi l'intero viaggio ancora in accelerazione, e una molla che ne
        // eredita la velocita' — l'oltrepasso e' slancio vero, non un calcio dato alla fine.
        launch {
          scaleX.animateTo(FluidMotion.PopoverWindowRunUpEnd, FluidMotion.PopoverWindowRunUp)
          scaleX.animateTo(1f, FluidMotion.PopoverWindowSettleX)
        }
        launch {
          scaleY.animateTo(FluidMotion.PopoverWindowRunUpEnd, FluidMotion.PopoverWindowRunUp)
          scaleY.animateTo(1f, FluidMotion.PopoverWindowSettleY)
        }
      } else {
        launch { scaleX.animateTo(1f, FluidMotion.PopoverOpenX) }
        launch { scaleY.animateTo(1f, FluidMotion.PopoverOpenY) }
      }
    } else if (journeyMode && entry.origin() != null) {
      // La chiusura della finestra e' UN gesto: la sagoma rientra nella riga (piu' rigida che
      // altrove, cosi' atterra PRIMA che la dissolvenza finisca) e l'immagine della riga le
      // riappare dentro strada facendo — quando il layer si smonta, quello che c'e' sotto e'
      // gia' identico. La versione morbida chiudeva la dissolvenza con la sagoma ancora a meta'
      // corsa: il pannello svaniva PER ARIA e la riga ricompariva di colpo — "fa degli effetti
      // pop", ed era vero.
      launch { fade.animateTo(0f, FluidMotion.fadeOut(FluidMotion.PopoverWindowFadeOutMillis)) }
      launch { scaleX.animateTo(0f, FluidMotion.PopoverWindowExit) }
      launch { scaleY.animateTo(0f, FluidMotion.PopoverWindowExit) }
    } else {
      // Leaving is critically damped: an overshoot on the way out reads as the pop-up bouncing off
      // the screen rather than being put away. But not *stiff* — at 900 the pane crossed most of
      // its travel inside two frames while a 130 ms fade was still at full alpha, and what the eye
      // caught was a modal that vanished with its text and a scrim left standing alone. The exit
      // has to be one gesture: the pane shrinks back onto the row it came from *while* it fades,
      // and the scrim, which reads the same fade, leaves with it.
      launch { fade.animateTo(0f, FluidMotion.fadeOut(FluidMotion.PopoverFadeOutMillis)) }
      launch { scaleX.animateTo(0f, FluidMotion.PopoverExit) }
      launch { scaleY.animateTo(0f, FluidMotion.PopoverExit) }
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
  val waitsForSprings = fluidModalSlidesUp(entry.presentation)
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
  var lastFooter by remember { mutableStateOf(entry.footer) }
  var lastOrigin by remember { mutableStateOf<Rect?>(null) }
  var lastPaneTitle by remember { mutableStateOf(entry.paneTitle) }
  // Tenuto come tutto il resto: alla chiusura la finestra deve continuare a essere del colore da
  // cui e' nata per tutto il viaggio di ritorno, non tornare grigia a meta' strada.
  var lastPaneTint by remember { mutableStateOf(entry.paneTint) }
  var lastPageColor by remember { mutableStateOf(entry.pageColor) }
  if (entry.visible) {
    lastContent = entry.content
    lastFooter = entry.footer
    lastOrigin = entry.origin()
    lastPaneTitle = entry.paneTitle
    lastPaneTint = entry.paneTint
    lastPageColor = entry.pageColor
    // Not only a context menu lifts its row out of the page: anything that morphs out of an anchor
    // does, because the pane is glass and the anchor would otherwise be legible through it.
    entry.lifted = true
  }

  // L'ancora VERA — la riga registrata da fluidExpandOrigin i cui limiti coincidono con l'origine.
  // Trovarla e' cio' che trasforma "un pannello compare dov'era la riga" in "la riga diventa il
  // pannello": la riga smette di disegnarsi nello stesso fotogramma in cui la finestra appare
  // sulla sua sagoma, e la sua immagine registrata parte col viaggio.
  var anchorNode by remember { mutableStateOf<FluidExpandAnchorNode?>(null) }
  if (entry.visible && sourceJourney == null && journeyMode && entry.presentation != FluidGlassModalPresentation.ContextMenu) {
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
  if (!entry.visible && (sourceJourney?.returned ?: exitFinished)) {
    // Whatever was lifted out of the page is handed back to it here, and not one frame earlier.
    SideEffect {
      entry.lifted = false
      entry.sheetBackground.undimmed = false
      entry.sheetBackground.dim = 1f
      anchorNode?.hidden = false
      anchorNode = null
    }
    return
  }

  val focusRequester = remember { FocusRequester() }
  LaunchedEffect(entry.visible) {
    if (entry.visible && entry.blocksPage) runCatching { focusRequester.requestFocus() }
  }

  // Predictive back drives the same retreat the drag does, so letting go halfway puts the modal
  // back where it was instead of jumping.
  var backProgress by remember { mutableFloatStateOf(0f) }
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
    FluidPresentationScrim(
      entry = entry,
      backdrop = backdrop,
      exports = scrimGlass,
      intensity = { presence() * (1f - backProgress * 0.6f) },
      plain = entry.mode != null,
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

    if (fluidModalSlidesUp(entry.presentation)) {
      FluidGlassModalSheet(
        paneTitle = lastPaneTitle,
        paneTint = lastPaneTint,
        pageColor = lastPageColor,
        backdrop = popoverBackdrop,
        // Sheets arrive by *travelling*, not by fading: the spring is the animation, and the alpha
        // only exists so the last few pixels of the exit do not pop.
        slide = { scaleY.value },
        backProgress = { backProgress },
        reducedMotion = reducedMotion,
        fullScreen = entry.presentation == FluidGlassModalPresentation.FullScreen,
        onDismiss = entry.onDismissRequest,
        footer = lastFooter,
        content = lastContent,
        sheetBackground = entry.sheetBackground,
      )
      return@Box
    }

    // Le azioni (menu contestuale, Expand) hanno il loro layer, `FluidMenuLayer`: qui arrivano solo
    // i pannelli di contenuto, anche quando dichiarano una di quelle presentazioni.
    val menu = entry.presentation == FluidGlassModalPresentation.ContextMenu
    val expand = entry.presentation == FluidGlassModalPresentation.Expand

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
      paneTitle = lastPaneTitle ?: if (menu || expand) LocalFluidLabels.current.actions else null,
      // Il menu contestuale no: il colore sta sulla riga sollevata, che e' l'oggetto. Una lista di
      // azioni tinta come la cosa da cui nasce diventa il secondo oggetto colorato della scena, e
      // a quel punto non si capisce piu' quale dei due si stia guardando.
      paneTint = if (menu) null else lastPaneTint,
      backdrop = popoverBackdrop,
      compact = menu || expand,
      // Tutto quello che nasce da un'ancora si apre **sopra di lei**, tranne il menu contestuale:
      // quello ha la riga sollevata li' accanto, e crescere sopra la riga significherebbe coprire
      // proprio l'oggetto che il sollevamento sta presentando.
      overAnchor = !menu,
      anchorRadiusPx = anchorRadiusPx,
      outgoing = anchorNode?.layer,
      windowBackdrop = backdrop,
      open = { entry.visible },
      presence = presence,
      growth = { scaleX.value },
      growthCross = { scaleY.value },
      retreat = { backProgress },
      sourceJourney = sourceJourney,
      modal = entry.blocksPage,
      parallelHitArea = entry.outsideTapArea.takeIf { !entry.blocksPage },
    ) {
      lastContent()
    }
  }
}

/**
 * Sotto un foglio la sorgente dello scrim è un colore, campionato dal vetro alla forza del fotogramma.
 *
 * Un foglio scurisce la pagina di più degli altri modali ([fluidSheetDimColor], 23% come Apple, contro il 12% di
 * avvisi e popover modali) e del tanto che il foglio dice ([FluidSheetBackground.dim]): oltre la soglia di
 * `undimmedUpTo` il velo cresce con l'altezza del foglio. La frazione si legge solo qui nel disegno, così un foglio
 * trascinato ridisegna lo scrim e non lo ricompone.
 */
@Composable
internal fun FluidPresentationScrim(
  entry: FluidGlassModalEntry,
  backdrop: GlassBackdropState?,
  exports: GlassBackdropState,
  intensity: () -> Float,
  plain: Boolean,
) {
  if (!plain) {
    if (entry.blocksPage) FluidGlassModalScrim(backdrop, exports, intensity, entry.visible, entry.onDismissRequest)
    return
  }
  if (entry.mode == FluidPresentationMode.Parallel || entry.inlineConfirmation) return
  val dark = GlassDefaults.isDarkSurface()
  val sheet = entry.presentation == FluidGlassModalPresentation.Sheet
  val tint = if (sheet) fluidSheetDimColor(dark) else GlassPaintedScrim.color(dark)
  Box(
    Modifier.fillMaxSize()
      .then(if (entry.visible && entry.blocksPage) Modifier.pointerInput(entry) {
        detectTapGestures { entry.onDismissRequest() }
      } else Modifier)
      .glassMaterialSource(exports)
      .drawBehind {
        if (entry.blocksPage) {
          val dim = if (sheet) entry.sheetBackground.dim.coerceIn(0f, 1f) else 1f
          drawRect(tint.copy(alpha = tint.alpha * intensity().coerceIn(0f, 1f) * dim))
        }
      },
  )
}

internal class FluidModalHitArea {
  var bounds: Rect = Rect.Zero
}

/** Riserva i tap sulla superficie al pannello anche nei punti privi di azioni. */
internal fun Modifier.fluidParallelModalPanel(): Modifier =
  pointerInput(Unit) { detectTapGestures { } }

/** Va sulla radice dell'host perché Compose cerca la condivisione sul fratello diretto della pagina. */
private data class FluidParallelModalTapElement(
  val area: FluidModalHitArea,
  val slop: Float,
  val onDismiss: () -> Unit,
) : ModifierNodeElement<FluidParallelModalTapNode>() {
  override fun create() = FluidParallelModalTapNode(area, slop, onDismiss)
  override fun update(node: FluidParallelModalTapNode) {
    node.area = area
    node.slop = slop
    node.onDismiss = onDismiss
  }
  override fun InspectorInfo.inspectableProperties() { name = "fluidParallelModalOutsideTap" }
}

private class FluidParallelModalTapNode(
  var area: FluidModalHitArea,
  var slop: Float,
  var onDismiss: () -> Unit,
) : Modifier.Node(), PointerInputModifierNode {
  private var pointer: PointerId? = null
  private var origin = Offset.Zero

  override fun sharePointerInputWithSiblings(): Boolean = true

  override fun onPointerEvent(pointerEvent: PointerEvent, pass: PointerEventPass, bounds: IntSize) {
    if (pass != PointerEventPass.Initial) return
    val change = pointerEvent.changes.singleOrNull() ?: run { onCancelPointerInput(); return }
    val position = change.position
    if (!change.previousPressed && change.pressed) {
      pointer = change.id.takeIf { !area.bounds.contains(position) }
      origin = position
    } else if (pointer == change.id) {
      if ((position - origin).getDistance() > slop) onCancelPointerInput()
      if (!change.pressed && pointer != null) {
        onCancelPointerInput()
        if (!area.bounds.contains(position)) onDismiss()
      }
    }
  }

  override fun onCancelPointerInput() { pointer = null }
}
