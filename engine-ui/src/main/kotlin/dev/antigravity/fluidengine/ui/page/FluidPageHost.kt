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
 *   - i passi fra schede restano quelli di prima (13%, la vecchia sfuma), senza carta e senza gesto.
 */
package dev.antigravity.fluidengine.ui.page

import dev.antigravity.fluidengine.ui.page.FluidPageHostState.PageRole

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.contentColorFor
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.ProvidableCompositionLocal
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.State
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.LocalSaveableStateRegistry
import androidx.compose.runtime.saveable.SaveableStateHolder
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.isSpecified
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.takeOrElse
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onPlaced
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.semantics.clearAndSetSemantics
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
 * - [content]: la pagina di una chiave, anche di una che ha appena lasciato la pila e sta uscendo.
 */
@Composable
fun <K : Any> FluidPageHost(
  stack: List<K>,
  onBack: () -> Unit,
  modifier: Modifier = Modifier,
  transition: (K) -> FluidPageTransition = { FluidPageTransition.Slide },
  backSwipe: (K) -> FluidBackSwipe = { FluidBackSwipe.Anywhere },
  peer: FluidPeerMove<K>? = null,
  onTransitionStart: (List<K>) -> Unit = {},
  onSettled: () -> Unit = {},
  leaving: Set<K>? = null,
  state: FluidPageHostState = rememberFluidPageHostState(),
  pageColor: Color = MaterialTheme.colorScheme.background,
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
    content = content,
  )
}

/**
 * [ownOwners]: l'host da' a ogni pagina il suo stato salvabile e il suo ciclo di vita. FluidNavHost
 * lo spegne, perche' quei due li da' gia' la voce del back stack.
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

  // Il back di sistema: registrato qui, prima che le pagine compongano i loro BackHandler, cosi'
  // quelli (e un modale aperto dopo) passano davanti.
  val dispatcherOwner: NavigationEventDispatcherOwner? = LocalNavigationEventDispatcherOwner.current
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
    state.peerOf = peer?.let { move -> { from: Any, to: Any -> move.direction(from as K, to as K) } }
    state.renderable = { page -> leaving == null || page in stack || page in leaving }
    state.onStack(stack)
    state.backHandler?.isBackEnabled = stack.size > 1
    // Lo stato salvato di una pagina che ha lasciato la pila (e lo schermo) non serve piu'.
    savedKeys.removeAll { saved ->
      val gone = saved !in stack && saved != state.upper && saved != state.lower
      if (gone) holder.removeState(saved)
      gone
    }
  }

  // Prima della prima chiusura a riposo lo stato non ha ancora le sue pagine: si disegna la pila.
  val upper = if (state.initialized) state.upper as K? else stack.lastOrNull()
  val lower = (if (state.initialized) state.lower as K? else stack.getOrNull(stack.size - 2))
    ?.takeIf { it != upper }

  Box(
    modifier = modifier
      .onPlaced { coordinates -> state.updateGeometry(coordinates, view) }
      .clipToBounds()
      .nestedScroll(remember(state) { FluidPageNestedScroll(state) })
      .fluidPageBackSwipe(state),
  ) {
    // L'ordine e' quello del disegno: sotto, poi sopra. Le chiavi tengono la composizione di una
    // pagina quando passa da sopra a sotto o viceversa.
    for (page in listOfNotNull(lower, upper)) {
      key(page) {
        PageLayer(
          page = page,
          isUpper = page == upper,
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
    // Sopra le pagine, sotto la chrome dell'app: una barra per host, ferma mentre le pagine passano.
    FluidAnchoredBar(state = state, registry = barRegistry, lowerKey = lower, upperKey = upper)
  }
}

@Composable
private fun <K : Any> PageLayer(
  page: K,
  isUpper: Boolean,
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
  val barSlot = remember(page, barRegistry) { FluidPageBarSlot(page, barRegistry) }
  val covered = !isUpper && state.kind == FluidPageMove.None
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
      .pageLayer(state, page, shadowColor, elevationPx, signals)
      .then(if (pageColor.isSpecified) Modifier.background(pageColor) else Modifier)
      .pageInputGate(state, page)
      .then(if (covered) Modifier.clearAndSetSemantics { } else Modifier),
  ) {
    CompositionLocalProvider(
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
  val scale = state.pageScale(role)
  scaleX = scale
  scaleY = scale
  alpha = 1f
  transformOrigin = TransformOrigin.Center
  shape = RectangleShape
  clip = false
  shadowElevation = 0f
  when {
    role == PageRole.Gone -> Unit
    peer -> {
      if (role == PageRole.Upper) {
        if (state.peerTime < 1f) signals.markMoving()
      } else {
        // L'unica trasparenza su una pagina, ed e' quella di sempre delle schede: la pagina che esce
        // sta *sotto* quella che arriva, opaca, e sfuma nei suoi 150 ms.
        alpha = FluidPageMotion.peerFrame(state.peerTime, state.peerEased, state.width, state.peerDirection).third
      }
    }
    role == PageRole.Upper -> {
      if (state.opening && state.upperTransition == FluidPageTransition.Expand) {
        transformOrigin = state.expandOrigin
      }
      if (state.travelPx > FluidPageMotion.CardThresholdPx || scale < 1f) {
        shape = state.cardShape
        clip = true
        shadowElevation = elevationPx
        ambientShadowColor = shadowColor
        spotShadowColor = shadowColor
        signals.markMoving()
      }
    }
    else -> Unit
  }
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
  val owner = remember(parent) { CappedLifecycleOwner(parent) }
  DisposableEffect(owner) {
    owner.attach()
    onDispose { owner.detach() }
  }
  SideEffect { owner.setCap(if (capped) Lifecycle.State.CREATED else Lifecycle.State.RESUMED) }
  CompositionLocalProvider(LocalLifecycleOwner provides owner, content = content)
}

private class CappedLifecycleOwner(private val parent: LifecycleOwner) : LifecycleOwner {
  private val registry = LifecycleRegistry(this)
  private var cap = Lifecycle.State.RESUMED
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
