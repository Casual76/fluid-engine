/*
 * L'host delle pagine (3.0, fase 8): due strati opachi impilati, la pagina come carta.
 *
 * I due strati e la carta sono portati da LibrePods, **GPL-3.0**:
 *
 *   me.kavishdevar.librepods.presentation.navigation.SwipeBack.kt
 *     righe 198-262 (pagina sotto e pagina sopra in una Box, traslate in `graphicsLayer` in disegno)
 *   me.kavishdevar.librepods.presentation.components.primitives.StyledScaffold.kt
 *     righe 111-127 (gli angoli del telefono, `rootWindowInsets.getRoundedCorner`)
 *     righe 227-241 (ombra di 36 dp, nera su superficie chiara e grigia su scura, poi il ritaglio)
 *   https://github.com/librepods-org/librepods, branch `android/rewrite`, commit 3d4d270.
 *
 * Licenza: GPL-3.0, https://www.gnu.org/licenses/gpl-3.0.html; vedi LICENSES/LibrePods.md.
 *
 * **Cosa abbiamo cambiato.**
 *   - la carta (angoli e ombra) c'e' solo mentre la pagina si muove: a riposo il layer non ritaglia
 *     e non proietta niente; gli angoli sono quelli del telefono solo dove l'host tocca un angolo
 *     dello schermo;
 *   - la pagina sotto e' composta ma non disegnata mentre e' coperta, fuori dall'albero semantico e
 *     col ciclo di vita fermo a CREATED (con FluidNavHost lo fa il NavController);
 *   - chi e' davanti passa di mano a meta' uscita (`LocalFluidRouteFront`), in AND col davanti di chi
 *     contiene l'host; il vetro si assottiglia mentre due pagine si muovono (`markMoving`);
 *   - i passi fra schede restano quelli di prima (13%, la vecchia sfuma), senza carta e senza gesto;
 *   - (4.0, F5B.3) con una sorgente dichiarata ([FluidPageHost] `zoomSource`) Expand e' uno zoom: la pagina sta dentro
 *     una sagoma che viaggia fra la sorgente e lo schermo, ritagliata e in scala (`FluidZoom`).
 */
package dev.antigravity.fluidengine.ui.page

import dev.antigravity.fluidengine.ui.page.FluidPageHostState.PageRole

import androidx.compose.foundation.background
import androidx.compose.foundation.shape.AbsoluteRoundedCornerShape
import androidx.compose.ui.graphics.rememberGraphicsLayer
import androidx.compose.ui.graphics.TileMode
import androidx.compose.ui.unit.Constraints
import dev.antigravity.fluidengine.ui.fluid.GlassBackdropState
import dev.antigravity.fluidengine.ui.fluid.GlassDefaults
import dev.antigravity.fluidengine.ui.fluid.GlassRole
import dev.antigravity.fluidengine.ui.fluid.glassBackdropSource
import dev.antigravity.fluidengine.ui.fluid.glassContentBlurEffect
import dev.antigravity.fluidengine.ui.fluid.glassSurface
import dev.antigravity.fluidengine.ui.fluid.rememberGlassBackdrop
import dev.antigravity.fluidengine.ui.glass.backdrop.isRenderEffectSupported
import kotlin.math.roundToInt
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.contentColorFor
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.ProvidableCompositionLocal
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.State
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.retain.retain
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.LocalSaveableStateRegistry
import androidx.compose.runtime.saveable.SaveableStateHolder
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.clipToBounds
import dev.antigravity.fluidengine.ui.theme.BlackMediaAppearance
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.isSpecified
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.layer.drawLayer
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.takeOrElse
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.layout
import androidx.compose.ui.layout.onPlaced
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.hideFromAccessibility
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.navigationevent.DirectNavigationEventInput
import androidx.navigationevent.NavigationEventDispatcherOwner
import androidx.navigationevent.compose.LocalNavigationEventDispatcherOwner
import dev.antigravity.fluidengine.ui.fluid.FluidRouteSignal
import dev.antigravity.fluidengine.ui.fluid.FluidTransitionSource
import dev.antigravity.fluidengine.ui.fluid.fluidGlassTraced
import dev.antigravity.fluidengine.ui.fluid.LocalCoveredGlassView
import dev.antigravity.fluidengine.ui.fluid.LocalFluidMotionPolicy
import dev.antigravity.fluidengine.ui.theme.LocalFluidRouteFront
import dev.antigravity.fluidengine.ui.theme.LocalRouteMotionSignals
import dev.antigravity.fluidengine.ui.theme.RouteMotionSignals

/** Quello che una pagina sa di se' dentro l'host. */
@Stable
interface FluidPageScope {
  /** Lo stato dell'host: che movimento c'e', quanto, se c'e' un dito. */
  val transition: FluidPageHostState

  /** Questa pagina e' quella sotto, coperta, a riposo: composta ma non disegnata. */
  val isCovered: Boolean

  /** Questa pagina e' quella davanti adesso (passa di mano a meta' uscita). */
  val isFront: Boolean
}

/** L'host di pagine piu' vicino, o null fuori da un host. */
val LocalFluidPageTransition: ProvidableCompositionLocal<FluidPageHostState?> =
  staticCompositionLocalOf { null }

/** Una schermata misurata fuori dalla pila non può ancora offrire il proprio fondale alla shell. */
internal val LocalFluidPagePreparing = compositionLocalOf { false }

/** Vive attraverso la configurazione, come la pila. Una nuova istanza di processo parte a riposo. */
private class RotationMemory(var trip: VerticalPageRotation? = null)

/**
 * Un host di pagine: la pila che riceve diventa due strati opachi, quello sopra e quello sotto, e
 * ogni cambio diventa un movimento con una molla. Non sa niente di navigation: per un `NavController`
 * c'e' `FluidNavHost`, che lo usa.
 *
 * - [stack]: la pila obiettivo, dalla radice alla cima. Le chiavi devono restare uguali (equals) fra
 *   una composizione e l'altra; l'host fa il diff: cima nuova = push, cima che c'era = pop, pari =
 *   passo fra schede.
 * - [onBack]: un gesto confermato (o il back di sistema senza gesto) lo chiama *prima* che la pagina
 *   finisca di uscire; l'app accorcia la pila e la molla continua. Se l'app non la accorcia, la
 *   pagina torna dov'era. **Non lo chiama se una pagina ha un `BackHandler` attivo** (3.0): un back
 *   confermato — dal sistema o dal dito dentro l'app — passa dal dispatcher, e il gestore abilitato
 *   registrato per ultimo risponde. Quello dell'host sta sotto i `BackHandler` delle pagine, quindi
 *   un editor che chiede conferma prima di uscire la chiede anche al dito: la pagina segue il dito,
 *   e se il gesto supera la soglia torna a posto mentre il gestore gira.
 * - [onTransitionStart]: le due pagine che il dito comincia a separare (cima, sotto).
 * - [onSettled]: l'host e' tornato a riposo.
 * - [leaving]: le chiavi che hanno lasciato la pila ma si possono ancora disegnare (null: tutte).
 * - [pageColor]: il fondo di ogni pagina, di serie `colorScheme.background`. Trasparente, o
 *   [Color.Unspecified], l'host non dipinge niente e ogni pagina lascia vedere quello che c'e'
 *   dietro: le due pagine di un movimento non sono piu' due strati opachi e si vedono per
 *   trasparenza mentre una scivola sull'altra. `FluidNavHost` lo passa uguale.
 * - [zoomSource]: la sorgente da cui una pagina [FluidPageTransition.Expand] si apre con lo zoom (4.0, F5B.3; in SwiftUI
 *   `navigationTransition(.zoom(sourceID:in:))`), di solito `FluidTransitionSource(id, namespace)` con lo stesso id del
 *   `Modifier.fluidTransitionSource` messo sulla card. La pagina nasce dal riquadro e dalla forma della sorgente, ci torna
 *   al pop e col dito, e intanto la sorgente non si disegna. Null, o una sorgente che in quel momento non si trova
 *   (uscita dallo scorrimento): l'Expand di sempre, dal punto toccato. Le altre transizioni la ignorano.
 * - [content]: la pagina di una chiave, anche di una che ha appena lasciato la pila e sta uscendo.
 */
@Composable
fun <K : Any> FluidPageHost(
  stack: List<K>,
  onBack: () -> Unit,
  modifier: Modifier = Modifier,
  transition: (K) -> FluidPageTransition = { FluidPageTransition.Slide },
  backSwipe: (K) -> FluidBackSwipe = { FluidBackSwipe.EdgeOnly },
  peer: FluidPeerMove<K>? = null,
  onTransitionStart: (List<K>) -> Unit = {},
  onSettled: () -> Unit = {},
  leaving: Set<K>? = null,
  state: FluidPageHostState = rememberFluidPageHostState(),
  pageColor: Color = MaterialTheme.colorScheme.background,
  zoomSource: (K) -> FluidTransitionSource? = { null },
  content: @Composable FluidPageScope.(K) -> Unit,
) {
  FluidPageHostImpl(
    stack = stack, onBack = onBack, modifier = modifier, transition = transition, backSwipe = backSwipe,
    peer = peer, onTransitionStart = onTransitionStart, onSettled = onSettled, leaving = leaving,
    state = state, pageColor = pageColor, ownOwners = true, pageKey = { it }, zoomSource = zoomSource,
    content = content,
  )
}

/**
 * Variante per una destinazione già nota alla pagina chiamante. A riposo l'host può comporre e misurare
 * [precomposePage] nella sua sede definitiva prima del tocco. La pagina preparata ha ciclo di vita CREATED,
 * resta fuori dal disegno e dai tocchi e può essere scartata se cambia la previsione. Gli effetti di
 * composizione possono partire: riservare le azioni di apertura esplicita al davanti o al ciclo di vita.
 * Null disattiva la preparazione. Una destinazione Expand si prepara soltanto al momento della navigazione.
 */
@Composable
fun <K : Any> FluidPageHost(
  stack: List<K>,
  onBack: () -> Unit,
  modifier: Modifier = Modifier,
  transition: (K) -> FluidPageTransition = { FluidPageTransition.Slide },
  backSwipe: (K) -> FluidBackSwipe = { FluidBackSwipe.EdgeOnly },
  peer: FluidPeerMove<K>? = null,
  onTransitionStart: (List<K>) -> Unit = {},
  onSettled: () -> Unit = {},
  leaving: Set<K>? = null,
  state: FluidPageHostState = rememberFluidPageHostState(),
  pageColor: Color = MaterialTheme.colorScheme.background,
  zoomSource: (K) -> FluidTransitionSource? = { null },
  precomposePage: K?,
  content: @Composable FluidPageScope.(K) -> Unit,
) {
  FluidPageHostImpl(
    stack = stack,
    onBack = onBack,
    modifier = modifier,
    transition = transition,
    backSwipe = backSwipe,
    peer = peer,
    onTransitionStart = onTransitionStart,
    onSettled = onSettled,
    leaving = leaving,
    state = state,
    pageColor = pageColor,
    ownOwners = true,
    pageKey = { it },
    zoomSource = zoomSource,
    precomposePage = precomposePage,
    content = content,
  )
}

/** [dismiss] abilita per destinazione il trascinamento verso il basso, anche dopo una lista annidata. */
@Composable
fun <K : Any> FluidPageHost(
  stack: List<K>,
  onBack: () -> Unit,
  modifier: Modifier = Modifier,
  transition: (K) -> FluidPageTransition = { FluidPageTransition.Slide },
  backSwipe: (K) -> FluidBackSwipe = { FluidBackSwipe.EdgeOnly },
  peer: FluidPeerMove<K>? = null,
  onTransitionStart: (List<K>) -> Unit = {},
  onSettled: () -> Unit = {},
  leaving: Set<K>? = null,
  state: FluidPageHostState = rememberFluidPageHostState(),
  pageColor: Color = MaterialTheme.colorScheme.background,
  zoomSource: (K) -> FluidTransitionSource? = { null },
  precomposePage: K? = null,
  dismiss: (K) -> FluidPageDismiss,
  content: @Composable FluidPageScope.(K) -> Unit,
) {
  FluidPageHostImpl(stack, onBack, modifier, transition, backSwipe, peer, onTransitionStart, onSettled,
    leaving, state, pageColor, ownOwners = true, pageKey = { it }, zoomSource = zoomSource,
    precomposePage = precomposePage, dismiss = dismiss, content = content)
}

/**
 * Con i preset di «un elemento che diventa pagina» (F6B.5c): [zoom] dà per ogni chiave il suo [FluidZoomStyle], null
 * per le pagine che non ne hanno. Una chiave con un preset è di serie [FluidPageTransition.Expand] e si chiude anche
 * trascinando in giù ([FluidZoomStyle.dismiss]); [transition] e [dismiss] possono dire altro. La sorgente è quella di
 * [zoomSource], che il preset completa: il vetro che cresce per [FluidZoomStyle.Grow] e per i tasti della barra, e il
 * tasto della barra della pagina sotto per [FluidZoomStyle.fromToolbar], che non chiede una [zoomSource].
 *
 * ```kotlin
 * FluidPageHost(stack, onBack = { stack = stack.dropLast(1) },
 *   zoom = { key -> if (key == "lettore") FluidZoomStyle.Rise else null },
 *   zoomSource = { key -> if (key == "lettore") FluidTransitionSource("mini", ns) else null }) { key -> … }
 * ```
 */
@Composable
fun <K : Any> FluidPageHost(
  stack: List<K>,
  onBack: () -> Unit,
  zoom: (K) -> FluidZoomStyle?,
  modifier: Modifier = Modifier,
  transition: (K) -> FluidPageTransition = { key ->
    if (zoom(key) != null) FluidPageTransition.Expand else FluidPageTransition.Slide
  },
  backSwipe: (K) -> FluidBackSwipe = { FluidBackSwipe.EdgeOnly },
  peer: FluidPeerMove<K>? = null,
  onTransitionStart: (List<K>) -> Unit = {},
  onSettled: () -> Unit = {},
  leaving: Set<K>? = null,
  state: FluidPageHostState = rememberFluidPageHostState(),
  pageColor: Color = MaterialTheme.colorScheme.background,
  zoomSource: (K) -> FluidTransitionSource? = { null },
  precomposePage: K? = null,
  dismiss: (K) -> FluidPageDismiss = { key -> zoom(key)?.dismiss ?: FluidPageDismiss.None },
  content: @Composable FluidPageScope.(K) -> Unit,
) {
  FluidPageHostImpl(stack, onBack, modifier, transition, backSwipe, peer, onTransitionStart, onSettled,
    leaving, state, pageColor, ownOwners = true, pageKey = { it }, zoomSource = zoomSource,
    precomposePage = precomposePage, dismiss = dismiss, zoom = zoom, content = content)
}

/**
 * [ownOwners]: l'host da' a ogni pagina il suo stato salvabile e il suo ciclo di vita. FluidNavHost
 * lo spegne, perche' quei due li da' gia' la voce del back stack.
 *
 * [pageKey]: la chiave con cui una pagina sta in composizione. Deve avere un `hashCode()` che sopravvive
 * alla ricreazione dell'Activity: la chiave di ogni `rememberSaveable` dentro la pagina deriva dall'hash
 * di composizione, che include quello di questa chiave. La `NavBackStackEntry` ne ha uno che dipende da
 * oggetti nuovi a ogni ricreazione, quindi FluidNavHost passa il suo `id`, come fa `NavHost`.
 */
@Suppress("UNCHECKED_CAST")
@Composable
internal fun <K : Any> FluidPageHostImpl(
  stack: List<K>,
  onBack: () -> Unit,
  modifier: Modifier,
  transition: (K) -> FluidPageTransition,
  backSwipe: (K) -> FluidBackSwipe,
  peer: FluidPeerMove<K>?,
  onTransitionStart: (List<K>) -> Unit,
  onSettled: () -> Unit,
  leaving: Set<K>?,
  state: FluidPageHostState,
  pageColor: Color,
  ownOwners: Boolean,
  pageKey: (K) -> Any,
  zoomSource: (K) -> FluidTransitionSource? = { null },
  precomposePage: K? = null,
  dismiss: (K) -> FluidPageDismiss = { FluidPageDismiss.None },
  zoom: (K) -> FluidZoomStyle? = { null },
  handleSystemBack: Boolean = true,
  content: @Composable FluidPageScope.(K) -> Unit,
) {
  val scope = rememberCoroutineScope()
  val density = LocalDensity.current
  val layoutSign = if (LocalLayoutDirection.current == LayoutDirection.Ltr) 1 else -1
  val reducedMotion = LocalFluidMotionPolicy.current.reducedMotion
  val parentFront = LocalFluidRouteFront.current
  val signals = LocalRouteMotionSignals.current
  val view = LocalView.current
  val shadowColor = if (MaterialTheme.colorScheme.surface.luminance() > 0.5f) Color.Black else Color.Gray
  val elevationPx = with(density) { FluidPageMotion.CardElevationDp.dp.toPx() }
  val holder = rememberSaveableStateHolder()
  val savedKeys = remember { mutableSetOf<Any>() }
  // I posti delle barre delle pagine (3.0): ogni pagina descrive la sua, l'host ne disegna una.
  val barRegistry: FluidPageBarRegistry = remember { mutableStateMapOf() }
  // Le sorgenti dei preset di «un elemento che diventa pagina» (F6B.5c).
  val zoomSources = remember { FluidZoomSources() }

  // Il back di sistema: registrato qui, prima che le pagine compongano i loro BackHandler, cosi'
  // quelli (e un modale aperto dopo) passano davanti.
  val dispatcherOwner: NavigationEventDispatcherOwner? = if (handleSystemBack) LocalNavigationEventDispatcherOwner.current else null
  DisposableEffect(dispatcherOwner, state) {
    val dispatcher = dispatcherOwner?.navigationEventDispatcher
    val handler = FluidPageBackHandler(state)
    // L'ingresso da cui il dito dentro l'app manda il suo "indietro" confermato: allo stesso dispatcher
    // del sistema, cosi' a rispondere e' chi risponderebbe a un back di sistema.
    val fingerInput = DirectNavigationEventInput()
    val dispatchBack: () -> Unit = { fingerInput.backCompleted() }
    if (dispatcher != null) {
      dispatcher.addHandler(handler)
      dispatcher.addInput(fingerInput)
      handler.isBackEnabled = state.stackSize > 1
      state.backHandler = handler
      state.dispatchBack = dispatchBack
    }
    onDispose {
      if (dispatcher != null) {
        handler.remove()
        dispatcher.removeInput(fingerInput)
      }
      if (state.backHandler === handler) state.backHandler = null
      if (state.dispatchBack === dispatchBack) state.dispatchBack = null
    }
  }

  // Le sorgenti nascoste dalle pagine aperte con lo zoom rinascono se l'host esce di scena con le pagine aperte.
  val rotation = retain { RotationMemory() }
  val stableKey = rememberUpdatedState(pageKey)
  DisposableEffect(state) {
    state.restoreAfterRotation = rotation.trip
    rotation.trip = null
    onDispose {
      rotation.trip = state.rotationSnapshot { page -> stableKey.value(page as K) }
      state.releaseSources()
    }
  }

  // La cima della pila all'ultima chiusura: quando cambia, la rotta e' cambiata, e lo si dice a chi
  // sta fuori dalle pagine nella stessa finestra (3.0.1: la barra flottante si riapre e chiude il fermo
  // dello scorrimento di una lista che non c'e' piu').
  val lastTop = remember { arrayOfNulls<Any>(1) }
  SideEffect {
    val top = stack.lastOrNull()
    if (lastTop[0] != null && top != lastTop[0]) FluidRouteSignal.routeChanged(view)
    lastTop[0] = top
  }

  val top = stack.lastOrNull()
  val next = if (precomposePage != null && top != null && parentFront.value &&
    !reducedMotion && !state.isMoving && state.upper == top) {
    precomposePage.takeIf { it !in stack && transition(it) != FluidPageTransition.Expand &&
      (peer?.direction(top, it) ?: 0) == 0 }
  } else null
  // Lascia disegnare la pagina corrente prima di misurarne una seconda. Il lavoro viene annullato
  // se cambia la previsione. La promozione riusa la stessa chiave anche se il tocco arriva subito.
  var prepared by remember(state, next) { mutableStateOf<K?>(null) }
  if (next != null) LaunchedEffect(state, next) {
    withFrameNanos { }
    prepared = next
  }
  val entering = top?.takeIf {
    !reducedMotion && state.canComposePush(stack) && transition(it) != FluidPageTransition.Expand &&
      (peer?.direction(stack[stack.lastIndex - 1], it) ?: 0) == 0
  }

  SideEffect {
    state.scope = scope
    state.density = density.density
    state.layoutDirectionSign = layoutSign
    state.reducedMotion = reducedMotion
    state.onBack = onBack
    state.onTransitionStart = { pages -> onTransitionStart(pages as List<K>) }
    state.onSettled = onSettled
    state.transitionOf = { page -> transition(page as K) }
    state.backSwipeOf = { page -> backSwipe(page as K) }
    state.dismissOf = { page -> dismiss(page as K) }
    state.peerOf = peer?.let { move -> { from: Any, to: Any -> move.direction(from as K, to as K) } }
    state.renderable = { page -> leaving == null || page in stack || page in leaving }
    zoomSources.track(stack) { page -> page == state.upper || page == state.lower }
    state.sourceOf = { page ->
      val key = page as K
      zoomSources.sourceOf(page, zoom(key), zoomSource(key)) { below -> barRegistry[below] }
    }
    state.resolveSource = zoomSources::resolve
    state.onStack(stack)
    state.resumeRotation { page -> pageKey(page as K) }
    state.backHandler?.isBackEnabled = stack.size > 1
    // Lo stato salvato di una pagina che ha lasciato la pila (e lo schermo) non serve piu'.
    savedKeys.removeAll { saved ->
      val gone = saved !in stack && saved != state.upper && saved != state.lower && saved != prepared
      if (gone) holder.removeState(saved)
      gone
    }
  }

  // Prima della prima chiusura a riposo lo stato non ha ancora le sue pagine: si disegna la pila.
  val upper = entering ?: if (state.initialized) state.upper as K? else top
  val lower = when {
    entering != null -> state.upper as K?
    state.initialized -> state.lower as K?
    else -> stack.getOrNull(stack.size - 2)
  }?.takeIf { it != upper }

  Box(
    modifier = modifier
      .onPlaced { coordinates -> state.updateGeometry(coordinates, view) }
      .onGloballyPositioned {
        state.resumeRotation { page -> pageKey(page as K) }
        state.refreshPlacedSource()
      }
      .clipToBounds()
      .nestedScroll(remember(state) { FluidPageNestedScroll(state) })
      .fluidPageBackSwipe(state)
      .verticalPageInput(state),
  ) {
    // L'ordine e' quello del disegno: sotto, poi sopra. Le chiavi tengono la composizione di una
    // pagina quando passa da sopra a sotto o viceversa.
    val glass = state.growing
    val backdrop = if (glass != null) rememberGlassBackdrop() else null
    for (page in listOfNotNull(lower, upper, prepared)) {
      if (page == upper && glass != null && backdrop != null) {
        GrowingPageSurface(state, backdrop, glass)
      }
      val viewerColor = state.ownPageBackground[page]
      if (page == upper && viewerColor != null) {
        Box(Modifier.matchParentSize().drawBehind {
          if (state.hasClosingBackdrop(page)) drawRect(viewerColor, alpha = state.backdropArrival(page))
        })
      }
      key(pageKey(page)) {
        PageLayer(
          page = page,
          growingBackground = backdrop.takeIf { page == lower },
          isUpper = page == upper,
          preparedOnly = page == prepared,
          entering = entering != null,
          state = state,
          pageColor = pageColor,
          shadowColor = shadowColor,
          elevationPx = elevationPx,
          signals = signals,
          parentFront = parentFront,
          ownOwners = ownOwners,
          holder = holder,
          savedKeys = savedKeys,
          barRegistry = barRegistry,
          content = content,
        )
      }
    }
    if (state.zoom != null) SharedPageOverlay(state, Modifier.matchParentSize())
    // Sopra le pagine, sotto la chrome dell'app: una barra per host, ferma mentre le pagine passano.
    BlackMediaAppearance(upper?.let { state.ownPageBackground[it] } == Color.Black) {
      FluidAnchoredBar(state = state, registry = barRegistry,
        lowerKey = if (entering == null) lower else state.lower,
        upperKey = if (entering == null) upper else state.upper)
    }
  }
}

@Composable
private fun <K : Any> PageLayer(
  page: K,
  growingBackground: GlassBackdropState?,
  isUpper: Boolean,
  preparedOnly: Boolean,
  entering: Boolean,
  state: FluidPageHostState,
  pageColor: Color,
  shadowColor: Color,
  elevationPx: Float,
  signals: RouteMotionSignals,
  parentFront: State<Boolean>,
  ownOwners: Boolean,
  holder: SaveableStateHolder,
  savedKeys: MutableSet<Any>,
  barRegistry: FluidPageBarRegistry,
  content: @Composable FluidPageScope.(K) -> Unit,
) {
  val sharedSpace = remember(state, page) { SharedPageSpace(state, page) }
  val barSlot = remember(page, barRegistry) { FluidPageBarSlot(page, barRegistry) }
  val covered = preparedOnly || (!isUpper && !entering && state.kind == FluidPageMove.None)
  // La pagina che riappare sotto il dito è una previsione finché il gesto non viene confermato.
  val hiddenFromAccessibility = (entering && !isUpper) || state.blocksInput(page) ||
    (!isUpper && (state.phase == FluidPageHostState.Phase.Dragging || state.phase == FluidPageHostState.Phase.Cancelling))
  val coveredState = rememberUpdatedState(covered)
  // Il ruolo si legge dallo stato, non dalla composizione: vedi [pageLayer].
  val front = remember(state, page, parentFront) {
    derivedStateOf {
      val role = state.roleOf(page)
      parentFront.value && role != PageRole.Gone &&
        FluidPageMotion.pageIsFront(state.frontFraction(), isUpper = role == PageRole.Upper)
    }
  }
  val pageScope = remember(state, coveredState, front) { PageScopeImpl(state, coveredState, front) }
  // Chi dipinge il fondo dichiara il colore di quello che ci va sopra (3.0.1): l'host dipinge
  // [pageColor], quindi il testo di una pagina che non lo dice da se' (una pagina senza `FluidScreen`,
  // un'app senza `Surface` alla radice) e' quello del fondo, non il nero di `LocalContentColor`. Con una
  // pagina trasparente resta quello di fuori, che e' cio' che si vede dietro.
  val pageContent = if (pageColor.isSpecified && pageColor.alpha > 0f) {
    MaterialTheme.colorScheme.contentColorFor(pageColor).takeOrElse { LocalContentColor.current }
  } else {
    LocalContentColor.current
  }

  Box(
    modifier = Modifier
      .fillMaxSize()
      .then(if (preparedOnly) Modifier.layout { measurable, constraints ->
        val placeable = measurable.measure(constraints)
        layout(placeable.width, placeable.height) { /* Prima del push la pagina ha solo una misura. */ }
      } else Modifier)
      .then(
        if (isUpper && (entering || state.pendingPlacement == page)) {
          Modifier.onGloballyPositioned {
            if (state.pendingPlacement == page && state.zoom == null) {
              fluidGlassTraced("FluidPage.pushPlaced") { state.pagePlaced(page, System.nanoTime()) }
            }
          }
        } else Modifier,
      )
      .then(if (growingBackground != null) Modifier.growingBackdrop(state, growingBackground) else Modifier)
      .pageLayer(state, page, shadowColor, elevationPx, signals)
      .drawWithContent {
        drawContent()
        if (state.zoom == null || !state.zoomCrossfade) state.zoomFrontDrawn(page)
        val dim = state.dimOn(page)
        if (dim > 0f) drawRect(Color.Black, alpha = dim)
      }
      .onPlaced { sharedSpace.coordinates = it }
      .zoomCrossfade(state, page, pageColor)
      .then(if (pageColor.isSpecified && page !in state.ownPageBackground) Modifier.background(pageColor) else Modifier)
      .pageInputGate(state, page)
      .then(when {
        covered -> Modifier.clearAndSetSemantics { }
        hiddenFromAccessibility -> Modifier.clearAndSetSemantics { hideFromAccessibility() }
        else -> Modifier
      }),
  ) {
    CompositionLocalProvider(
      LocalCoveredGlassView provides (if (covered) LocalView.current else LocalCoveredGlassView.current),
      LocalSharedPage provides sharedSpace,
      LocalFluidPagePreparing provides (preparedOnly || LocalFluidPagePreparing.current),
      LocalFluidRouteFront provides front,
      LocalFluidPageTransition provides state,
      LocalFluidPageBar provides barSlot,
      LocalContentColor provides pageContent,
    ) {
      if (ownOwners) {
        CappedLifecycle(capped = covered) {
          val registry = LocalSaveableStateRegistry.current
          if (registry == null || registry.canBeSaved(page)) {
            savedKeys += page
            holder.SaveableStateProvider(page) { pageScope.content(page) }
          } else {
            pageScope.content(page)
          }
        }
      } else {
        pageScope.content(page)
      }
    }
  }
}

private class PageScopeImpl(
  override val transition: FluidPageHostState,
  private val covered: State<Boolean>,
  private val front: State<Boolean>,
) : FluidPageScope {
  override val isCovered: Boolean get() = covered.value
  override val isFront: Boolean get() = front.value
}

/** Registra il fondo dopo la sfocatura e il velo: anche la lente vede la pagina sottostante già attenuata. */
private fun Modifier.growingBackdrop(state: FluidPageHostState, backdrop: GlassBackdropState): Modifier =
  glassBackdropSource(backdrop).graphicsLayer {
    val amount = FluidZoom.atmosphere(state.fractionToward { it == state.upper })
    renderEffect = if (isRenderEffectSupported()) glassContentBlurEffect(
      amount * FluidPageMotion.GrowBlurDp * density, tileMode = TileMode.Clamp) else null
  }

@Composable
private fun GrowingPageSurface(state: FluidPageHostState, backdrop: GlassBackdropState, glass: GrowingPageGlass) {
  // A riquadro ormai fermo cambia ancora il contorno: anche lente e filo devono ricevere la nuova forma.
  val corners by remember(state) { derivedStateOf { state.zoomShape()?.corners ?: FluidPageCorners.Zero } }
  val shape = remember(corners) { AbsoluteRoundedCornerShape(corners.topLeft, corners.topRight, corners.bottomRight, corners.bottomLeft) }
  Box(Modifier.layout { measurable, constraints ->
    val bounds = state.zoomShape()?.bounds ?: Rect.Zero
    val child = measurable.measure(Constraints.fixed(bounds.width.roundToInt().coerceAtLeast(0),
      bounds.height.roundToInt().coerceAtLeast(0)))
    layout(constraints.maxWidth, constraints.maxHeight) {
      if (!bounds.isEmpty) child.place(bounds.left.roundToInt(), bounds.top.roundToInt())
    }
  }.glassSurface(backdrop, glass.tint ?: GlassDefaults.controlTint(), shape,
    role = GlassRole.Interactive, optics = glass.optics, nestInContainer = false))
}

/** Le due facce non condividono una finestra leggibile. L'immagine condivisa è su un altro layer
 * e rimane nitida anche quando tutta la chrome si sfoca. */
@Composable
private fun Modifier.zoomCrossfade(state: FluidPageHostState, page: Any, pageColor: Color): Modifier {
  if ((state.zoom == null && page !in state.zoomFaceOverrides) || state.upper != page || !state.zoomCrossfade) return this
  val face = rememberGraphicsLayer()
  val sourceFace = rememberGraphicsLayer()
  return drawWithContent {
    val zoom = state.zoomShape()
    val faceOverride = state.zoomFaceOverrides[page]
    if (faceOverride != null && state.upper == page && !state.reducedMotion && state.sharedPair == null &&
      state.growing == null && (zoom != null || !state.isMoving)) {
      val source = state.zoomSnapshot()?.takeUnless { it.size.isEmpty() }
      val look = faceOverride(1f - state.upperExitFraction())
      if (source != null && (zoom != null || look.sourceAlpha > 0f || look.pageBlur > 0f || look.pageAlpha < 1f)) {
        val target = Rect(Offset.Zero, size)
        val clip = zoom?.let { FluidZoom.contentClip(it, target, FluidZoom.content(it, target)).bounds } ?: target
        // Child layers (including Android views) still need a display list while the source is visible.
        face.record {
          if (pageColor.isSpecified) drawRect(pageColor)
          this@drawWithContent.drawContent()
        }
        if (look.pageAlpha > 0f) {
          face.alpha = look.pageAlpha
          face.renderEffect = look.pageBlurEffect(density)
          drawLayer(face)
        }
        if (look.sourceAlpha > 0f && !clip.isEmpty) {
          // Keep the source's proportions throughout the zoom, including the full-page waiting pose.
          val scale = maxOf(clip.width / source.size.width, clip.height / source.size.height)
          val faceSize = source.size * scale
          val bounds = Rect(clip.center - Offset(faceSize.width / 2f, faceSize.height / 2f), faceSize)
          sourceFace.record(size = source.layer.size) { drawLayer(source.layer) }
          sourceFace.alpha = look.sourceAlpha
          sourceFace.renderEffect = zoomFaceEffect(look.sourceBlur, density)
          clipRect(clip.left, clip.top, clip.right, clip.bottom) { drawPageInk(sourceFace, bounds) }
        }
        if (!clip.isEmpty) state.zoomFrontDrawn(page)
        return@drawWithContent
      }
      // The source remains live until the front has a copy to show.
      if (zoom != null && source == null && state.awaitingZoomDraw(page) && look.sourceAlpha > 0f) return@drawWithContent
    }
    if (zoom == null || state.roleOf(page) != PageRole.Upper) {
      drawContent()
      return@drawWithContent
    }
    if (page in state.ownPageBackground && state.sharedPair == null) {
      drawContent()
      if (!size.isEmpty()) state.zoomFrontDrawn(page)
      return@drawWithContent
    }
    val target = Rect(Offset.Zero, size)
    val clip = FluidZoom.contentClip(zoom, target, FluidZoom.content(zoom, target)).bounds
    val look = state.zoomFaces()
    val grow = state.growing != null
    // Anche quando l'alfa è zero, i figli marcati possono registrarsi per il disegno dell'host.
    face.record { this@drawWithContent.drawContent() }
    face.alpha = if (grow) state.solidOpacity else look.pageAlpha
    val snapshot = state.zoomSnapshot()
    val sourceRect = snapshot?.takeIf { look.sourceAlpha > 0f && !it.size.isEmpty() }?.let {
      FluidZoom.snapshotRect(clip, it.size)
    }
    // Lascia vedere la griglia se il primo layer può disegnare soltanto il fondo della pagina.
    if (state.awaitingZoomDraw(page) && face.alpha <= 0f && sourceRect == null) return@drawWithContent
    if (!grow && pageColor.isSpecified && page !in state.ownPageBackground) drawRect(pageColor)
    if (face.alpha > 0f) {
      face.renderEffect = if (grow) null else look.pageBlurEffect(density)
      translate(top = if (grow) 0f else look.riseDp * density) { drawLayer(face) }
    }
    val drewSource = if (snapshot != null && sourceRect != null) {
      sourceFace.record(size = snapshot.layer.size) { drawLayer(snapshot.layer) }
      sourceFace.alpha = look.sourceAlpha
      sourceFace.renderEffect = zoomFaceEffect(look.sourceBlur, density)
      drawPageInk(sourceFace, sourceRect)
    } else false
    if (state.sharedPair == null && !clip.isEmpty && (drewSource || face.alpha > 0f)) state.zoomFrontDrawn(page)
  }
}

/**
 * Dove sta una pagina, tutto in disegno: nessuna ricomposizione per fotogramma.
 *
 * **Il ruolo (sopra, sotto, uscita) si legge dallo stato nel blocco del layer, non dalla
 * composizione.** La molla arriva a riposo dentro un fotogramma e la composizione che toglie la
 * pagina uscita arriva al fotogramma dopo: con il ruolo deciso in composizione, in quel fotogramma
 * la pagina appena uscita rileggeva una corsa a zero e tornava a coprire lo schermo per 16 ms (un
 * lampo, preso dal test del tasto indietro). Cosi' una pagina che lo stato non mostra piu' non si
 * disegna, e quella che e' diventata la cima si disegna a riposo, prima ancora di ricomporsi.
 *
 * Sopra: la carta (angoli del telefono, ombra) solo mentre si muove; mai alpha, mai RenderEffect,
 * mai scrim. Sotto: −(W/3)·(1 − e), ferma sotto una Expand che si apre, non disegnata a riposo
 * (e' coperta da una pagina opaca). I passi fra schede restano quelli di sempre.
 *
 * **Lo zoom (F5B.3).** La pagina sopra sta intera dentro la sua sagoma: in scala attorno al centro e spostata
 * ([FluidPageHostState.pageScale], [FluidPageHostState.pageOffsetX], [FluidPageHostState.pageOffsetY]: gli stessi numeri
 * che legge la barra per il titolo grande), ritagliata dalla sagoma riportata nelle sue coordinate. L'ombra cresce con
 * l'apertura: nel primo fotogramma la sagoma e' la sorgente, senza un'ombra che compare di colpo.
 */
private fun Modifier.pageLayer(
  state: FluidPageHostState,
  page: Any,
  shadowColor: Color,
  elevationPx: Float,
  signals: RouteMotionSignals,
): Modifier = drawWithContent {
  val visible = when (state.roleOf(page)) {
    PageRole.Upper -> true
    PageRole.Lower -> state.kind != FluidPageMove.None
    PageRole.Gone -> false
  }
  if (visible) drawContent()
}.graphicsLayer {
  val role = state.roleOf(page)
  val peer = state.kind == FluidPageMove.Peer
  // La geometria e' quella che legge anche la barra ancorata ([FluidPageHostState.pageOffsetX]):
  // il titolo grande, che e' contenuto, viaggia con la sua pagina allo stesso pixel.
  translationX = state.pageOffsetX(role)
  translationY = state.pageOffsetY(role)
  val scale = state.pageScale(role)
  scaleX = scale
  scaleY = scale
  val journey = state.zoom
  alpha = if (role == PageRole.Upper && journey?.withoutSource == true)
    ((1f - state.upperExitFraction()) / (1f - (journey.viaExit ?: 0f))).coerceIn(0f, 1f) else 1f
  transformOrigin = TransformOrigin.Center
  shape = RectangleShape
  clip = false
  shadowElevation = 0f
  when {
    role == PageRole.Gone -> Unit
    state.crossfading -> {
      // Si sfuma solo lo strato sopra: due alfa complementari lascerebbero trasparire il fondo dell'host.
      alpha = if (role == PageRole.Upper) 1f - state.frontFraction() else 1f
    }
    peer -> {
      if (role == PageRole.Upper) {
        if (state.peerTime < 1f) signals.markMoving()
      } else {
        // L'unica trasparenza su una pagina, ed e' quella di sempre delle schede: la pagina che esce
        // sta *sotto* quella che arriva, opaca, e sfuma nei suoi 150 ms.
        alpha = FluidPageMotion.peerFrame(state.peerTime, state.peerEased, state.width, state.peerDirection).third
      }
    }
    role == PageRole.Upper && state.zoomShape() != null -> {
      val zoomed = state.zoomShape()!!
      val target = Rect(0f, 0f, size.width, size.height)
      val clipped = FluidZoom.contentClip(zoomed, target, FluidZoom.content(zoomed, target))
      shape = FluidZoomClipShape(clipped.toRoundRect())
      clip = true
      // Cresce ha già l'ombra del materiale. Una carta trasparente sopra la lastra la disegnerebbe due volte.
      shadowElevation = if (state.growing != null || page in state.ownPageBackground) 0f else elevationPx * (1f - state.upperExitFraction()).coerceIn(0f, 1f)
      ambientShadowColor = shadowColor
      spotShadowColor = shadowColor
      signals.markMoving()
    }
    role == PageRole.Upper -> {
      if (state.opening && state.upperTransition == FluidPageTransition.Expand) {
        transformOrigin = state.expandOrigin
      }
      if (state.travelPx > FluidPageMotion.CardThresholdPx || scale < 1f) {
        shape = state.cardShape
        clip = true
        val slide = state.upperTransition == FluidPageTransition.Slide
        shadowElevation = when {
          page in state.ownPageBackground -> 0f
          slide -> size.width * FluidPageMotion.SlideElevationFraction
          else -> elevationPx
        }
        val color = if (slide) Color.Black.copy(alpha = FluidPageMotion.SlideShadowAlpha) else shadowColor
        ambientShadowColor = color
        spotShadowColor = color
        signals.markMoving()
      }
    }
    else -> Unit
  }
}

/**
 * La sagoma di uno zoom nelle coordinate del layer della pagina: un rettangolo arrotondato fuori dall'origine, che
 * l'hardware ritaglia gratis quando i raggi sono uguali (come la carta).
 */
private class FluidZoomClipShape(private val rect: RoundRect) : Shape {
  override fun createOutline(size: Size, layoutDirection: LayoutDirection, density: Density): Outline = Outline.Rounded(rect)
}

/** Durante una conferma la pagina che esce non prende tocchi; durante un push, quella coperta. */
private fun Modifier.pageInputGate(state: FluidPageHostState, page: Any): Modifier =
  pointerInput(state, page) {
    awaitPointerEventScope {
      while (true) {
        val event = awaitPointerEvent(PointerEventPass.Initial)
        if (state.blocksInput(page)) event.changes.forEach { it.consume() }
      }
    }
  }

/**
 * Il ciclo di vita di una pagina dell'host generico: quello di chi contiene l'host, fermo a CREATED
 * mentre la pagina e' coperta. Una pagina coperta resta composta (torna subito sotto il dito) ma
 * non raccoglie flussi legati a STARTED e non anima niente.
 */
@Composable
private fun CappedLifecycle(capped: Boolean, content: @Composable () -> Unit) {
  val parent = LocalLifecycleOwner.current
  val owner = remember(parent) { CappedLifecycleOwner(parent, capped) }
  DisposableEffect(owner) {
    owner.attach()
    onDispose { owner.detach() }
  }
  SideEffect { owner.setCap(if (capped) Lifecycle.State.CREATED else Lifecycle.State.RESUMED) }
  CompositionLocalProvider(LocalLifecycleOwner provides owner, content = content)
}

private class CappedLifecycleOwner(private val parent: LifecycleOwner, initiallyCapped: Boolean) : LifecycleOwner {
  private val registry = LifecycleRegistry(this)
  private var cap = if (initiallyCapped) Lifecycle.State.CREATED else Lifecycle.State.RESUMED
  private val observer = LifecycleEventObserver { _, _ -> update() }

  override val lifecycle: Lifecycle get() = registry

  fun attach() {
    parent.lifecycle.addObserver(observer)
    update()
  }

  fun detach() {
    parent.lifecycle.removeObserver(observer)
    if (registry.currentState.isAtLeast(Lifecycle.State.CREATED)) {
      registry.currentState = Lifecycle.State.DESTROYED
    }
  }

  fun setCap(state: Lifecycle.State) {
    cap = state
    update()
  }

  private fun update() {
    val outer = parent.lifecycle.currentState
    if (outer == Lifecycle.State.DESTROYED) {
      if (registry.currentState.isAtLeast(Lifecycle.State.CREATED)) {
        registry.currentState = Lifecycle.State.DESTROYED
      }
      return
    }
    if (outer == Lifecycle.State.INITIALIZED) return
    if (registry.currentState == Lifecycle.State.DESTROYED) return
    registry.currentState = if (outer < cap) outer else cap
  }
}
