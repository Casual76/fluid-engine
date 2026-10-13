package dev.antigravity.fluidengine.ui.fluid

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.FloatExponentialDecaySpec
import androidx.compose.animation.core.MutableTransitionState
import androidx.compose.animation.core.SpringSpec
import androidx.compose.animation.core.animate
import androidx.compose.animation.core.animateDecay
import androidx.compose.animation.core.spring
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.DraggableState
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Error
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.WarningAmber
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.ProvidableCompositionLocal
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.layout
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalAccessibilityManager
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.paneTitle
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.util.fastAny
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.currentStateAsState
import dev.antigravity.fluidengine.ui.haptics.FluidHapticEvent
import dev.antigravity.fluidengine.ui.haptics.FluidHaptics
import dev.antigravity.fluidengine.ui.haptics.NoOpFluidHaptics
import dev.antigravity.fluidengine.ui.haptics.rememberFluidHaptics
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.async
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.selects.select
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlin.math.abs
import kotlin.math.sign
import kotlin.math.sqrt

enum class FluidNotificationTone {
  Info,
  Success,
  Warning,
  Error,
}

/**
 * Un'azione della notifica (2.12.0): compare solo quando la card e' stata aperta tirandola in giu'.
 *
 * Toccarla la esegue e chiude la notifica. Una o due, con etichetta corta: stanno in una riga sotto
 * il testo, e una terza non avrebbe dove andare senza spingere la card fuori misura.
 */
@Immutable
data class FluidNotificationAction(
  val label: String,
  val onClick: () -> Unit,
)

/**
 * Una notifica in-app.
 *
 * La card si tocca con il dito (2.12.0): tenuta ferma il conto alla rovescia si ferma; tirata in giu'
 * si apre — testo intero e [actions] — e da aperta resta finche' non la chiudi tu; spinta in su
 * (o lanciata) se ne va. Per questo [message] puo' essere piu' lungo di quanto ne entra nella card
 * chiusa, che ne mostra due righe.
 *
 * @param actions mostrate solo a card aperta. Vuote per le notifiche che informano e basta.
 */
@Immutable
data class FluidNotification(
  val id: String,
  val title: String,
  val message: String,
  val tone: FluidNotificationTone = FluidNotificationTone.Info,
  val durationMillis: Long = 6_000L,
  val actions: List<FluidNotificationAction> = emptyList(),
)

/** A durable producer acknowledges only after the card has been genuinely presented. */
enum class FluidNotificationDelivery {
  Presented,
  Rejected,
}

private enum class FluidNotificationRequestCompletion {
  Presented,
  Interrupted,
}

private class FluidNotificationRequest(
  val notification: FluidNotification,
) {
  val presented = CompletableDeferred<Unit>()
  val exitCompleted = CompletableDeferred<Unit>()
  val completion = CompletableDeferred<FluidNotificationRequestCompletion>()
}

private sealed interface FluidNotificationTimerEvent {
  data class Dismissed(val id: String) : FluidNotificationTimerEvent
  data class HostActivityChanged(val isActive: Boolean) : FluidNotificationTimerEvent
  data object HoldChanged : FluidNotificationTimerEvent
  data object TimedOut : FluidNotificationTimerEvent
}

/**
 * Il minimo che resta alla card quando la lasci andare.
 *
 * Un dito che ha tenuto ferma la card per quattro secondi di sei non deve vederla sparire due
 * secondi dopo averla mollata, e uno che l'ha toccata all'ultimo istante non deve vederla sparire
 * sotto il dito: dopo un tocco c'e' sempre il tempo di accorgersi che e' ancora li'.
 */
internal const val NotificationResumeFloorMillis = 1_500L

/** Quanto conta ancora il tempo di lettura dopo un rilascio: quello che restava, mai meno del minimo. */
internal fun fluidNotificationResumeMillis(remainingMillis: Long): Long =
  remainingMillis.coerceAtLeast(NotificationResumeFloorMillis)

@Immutable
internal data class FluidNotificationPresentation(
  val notification: FluidNotification? = null,
  val visible: Boolean = false,
)

/**
 * A small FIFO for transient, app-wide messages.
 *
 * IDs are deduplicated while queued or visible. This matters for repository events: several screens
 * can observe the same sync completion, but the person should only ever see one notice for it.
 */
@Stable
class FluidNotificationHostState internal constructor() {
  private val requests = Channel<FluidNotificationRequest>(Channel.UNLIMITED)
  private val dismissals = Channel<String>(Channel.CONFLATED)
  private val pendingRequests = mutableMapOf<String, FluidNotificationRequest>()
  private val queueMutex = Mutex()
  private val runnerMutex = Mutex()
  private val hostActivityChanges = Channel<Boolean>(Channel.CONFLATED)
  private val holdChanges = Channel<Unit>(Channel.CONFLATED)
  @Volatile private var isHostActive = false

  // Le due cose che fermano il conto: un dito sulla card, o la card aperta. `releases` conta i
  // rilasci invece di ricordare un booleano: un tocco piu' breve di un giro del timer sparirebbe
  // fra due letture, e il minimo concesso dopo il rilascio andrebbe perso con lui.
  @Volatile private var isTouched = false
  @Volatile private var isExpanded = false
  @Volatile private var releases = 0
  private var retryFirst: FluidNotificationRequest? = null
  private var currentRequest: FluidNotificationRequest? = null

  internal var presentation by mutableStateOf(FluidNotificationPresentation())
    private set

  internal val currentNotification: FluidNotification?
    get() = presentation.notification.takeIf { presentation.visible }

  /**
   * Suspends until this ID is actually laid out by an active host. Concurrent calls for the same ID
   * await the same delivery instead of duplicating the banner. A host lifecycle interruption
   * transparently re-enqueues an unpresented request.
   */
  suspend fun show(notification: FluidNotification): FluidNotificationDelivery {
    if (notification.id.isBlank() || notification.message.isBlank()) {
      return FluidNotificationDelivery.Rejected
    }

    while (true) {
      var shouldEnqueue = false
      val request = queueMutex.withLock {
        pendingRequests[notification.id] ?: FluidNotificationRequest(notification).also {
          pendingRequests[notification.id] = it
          shouldEnqueue = true
        }
      }
      if (shouldEnqueue) {
        try {
          requests.send(request)
        } catch (error: Throwable) {
          withContext(NonCancellable) {
            queueMutex.withLock {
              if (pendingRequests[notification.id] === request) {
                pendingRequests.remove(notification.id)
              }
            }
            request.completion.complete(FluidNotificationRequestCompletion.Interrupted)
          }
          throw error
        }
      }

      when (request.completion.await()) {
        FluidNotificationRequestCompletion.Presented -> return FluidNotificationDelivery.Presented
        FluidNotificationRequestCompletion.Interrupted -> Unit
      }
    }
  }

  /** Called by the composed card after it has a real layout, not merely after being queued. */
  internal fun markPresented(id: String) {
    val request = currentRequest
    if (isHostActive && presentation.visible && request?.notification?.id == id) {
      request.presented.complete(Unit)
      request.completion.complete(FluidNotificationRequestCompletion.Presented)
    }
  }

  /** The host is active only while its lifecycle is resumed and its window owns focus. */
  internal fun setHostActive(isActive: Boolean) {
    if (isHostActive == isActive) return
    isHostActive = isActive
    hostActivityChanges.trySend(isActive)
  }

  /** Completes the FIFO hand-off only after AnimatedVisibility has actually settled off-screen. */
  internal fun markExitCompleted(id: String) {
    val request = currentRequest
    if (!presentation.visible && request?.notification?.id == id) {
      request.exitCompleted.complete(Unit)
    }
  }

  fun dismissCurrent() {
    currentNotification?.id?.let(dismissals::trySend)
  }

  /**
   * Un dito e' sulla card (o ne e' appena sceso). Finche' c'e' il tempo di lettura non scorre; al
   * rilascio riparte da quello che restava, ma non da meno di [NotificationResumeFloorMillis].
   * Una notifica che non e' piu' quella in scena non ascolta: il dito era sulla precedente.
   */
  internal fun setTouched(id: String, touched: Boolean) {
    if (currentRequest?.notification?.id != id || isTouched == touched) return
    isTouched = touched
    if (!touched) releases++
    holdChanges.trySend(Unit)
  }

  /**
   * La card e' stata aperta tirandola in giu': da qui non scade piu', resta finche' qualcuno la
   * chiude, tocca un'azione o la lancia via. Chi l'ha aperta ha deciso di leggerla, e un conto
   * alla rovescia sopra un testo lungo la porterebbe via a meta' riga.
   */
  internal fun setExpanded(id: String, expanded: Boolean) {
    if (currentRequest?.notification?.id != id || isExpanded == expanded) return
    isExpanded = expanded
    holdChanges.trySend(Unit)
  }

  internal suspend fun runQueue(
    recommendedTimeout: (FluidNotification) -> Long,
    monotonicTimeMillis: () -> Long = { System.nanoTime() / 1_000_000L },
  ) {
    // A state must never have two consumers: apart from corrupting currentRequest, two collectors
    // could let B overtake an interrupted A. The second host waits until the first has fully left.
    runnerMutex.lock()
    try {
      while (true) {
        val request = queueMutex.withLock {
          retryFirst?.also { retryFirst = null }
        } ?: requests.receive()
        runRequest(request, recommendedTimeout, monotonicTimeMillis)
      }
    } finally {
      runnerMutex.unlock()
    }
  }

  private suspend fun runRequest(
    request: FluidNotificationRequest,
    recommendedTimeout: (FluidNotification) -> Long,
    monotonicTimeMillis: () -> Long,
  ) {
    val notification = request.notification
    try {
      currentRequest = request
      // Il dito e l'apertura sono della card che c'era: la nuova parte da ferma.
      isTouched = false
      isExpanded = false
      // Payload and visibility change atomically, so the previous FIFO item cannot re-enter for
      // a frame while a LaunchedEffect catches up with the new notification.
      presentation = FluidNotificationPresentation(notification = notification, visible = true)
      // markPresented is accepted only when the card has layout, the lifecycle is RESUMED and the
      // window owns focus. The reading budget below is then counted only during the same state.
      request.presented.await()
      val timeout = recommendedTimeout(notification).coerceAtLeast(NotificationResumeFloorMillis)
      awaitDismissalOrActiveTimeout(notification.id, timeout, monotonicTimeMillis)
      presentation = FluidNotificationPresentation(notification = notification, visible = false)
      // A duration constant cannot track a spring (or the system animator scale). Waiting for the
      // UI handshake keeps the outgoing payload intact until every exit channel is actually idle.
      request.exitCompleted.await()
    } finally {
      if (currentRequest === request) currentRequest = null
      isTouched = false
      isExpanded = false
      if (presentation.notification?.id == notification.id) {
        presentation = FluidNotificationPresentation(notification = notification, visible = false)
      }
      withContext(NonCancellable) {
        queueMutex.withLock {
          if (!request.completion.isCompleted) {
            // The current, unpresented item must stay ahead of items already in the channel.
            retryFirst = request
          } else if (pendingRequests[notification.id] === request) {
            pendingRequests.remove(notification.id)
          }
        }
      }
    }
  }

  private suspend fun awaitDismissalOrActiveTimeout(
    notificationId: String,
    timeoutMillis: Long,
    monotonicTimeMillis: () -> Long,
  ) {
    var remainingMillis = timeoutMillis
    var seenReleases = releases
    while (true) {
      // Il tempo finito vale anche se la finestra ha perso il fuoco proprio ora: non si aspetta di
      // riaverlo per dirlo. Una card tenuta o aperta, invece, non e' mai "finita".
      if (remainingMillis <= 0L && !isTouched && !isExpanded) return
      while (!isHostActive) {
        hostActivityChanges.receive()
      }

      // Il conto scorre solo a card libera e chiusa. Con un dito sopra, o aperta, si aspetta e
      // basta: un evento (rilascio, chiusura, cambio di finestra) e' l'unica cosa che sveglia.
      val counting = !isTouched && !isExpanded

      val activeStartedAt = monotonicTimeMillis()
      val event = awaitTimerEvent(if (counting) remainingMillis else null)
      if (counting) {
        val activeElapsed = (monotonicTimeMillis() - activeStartedAt)
          .coerceIn(0L, remainingMillis)
        remainingMillis -= activeElapsed
      }
      if (releases != seenReleases) {
        seenReleases = releases
        remainingMillis = fluidNotificationResumeMillis(remainingMillis)
      }

      when (event) {
        is FluidNotificationTimerEvent.Dismissed -> if (event.id == notificationId) return
        is FluidNotificationTimerEvent.HostActivityChanged -> Unit
        FluidNotificationTimerEvent.HoldChanged -> Unit
        FluidNotificationTimerEvent.TimedOut -> return
      }
    }
  }

  /** [remainingMillis] nullo = nessuna scadenza: si aspetta solo un evento. */
  private suspend fun awaitTimerEvent(remainingMillis: Long?): FluidNotificationTimerEvent =
    coroutineScope {
      val timeout = remainingMillis?.let { millis ->
        async {
          delay(millis)
          FluidNotificationTimerEvent.TimedOut
        }
      }
      try {
        select {
          dismissals.onReceive(FluidNotificationTimerEvent::Dismissed)
          hostActivityChanges.onReceive(FluidNotificationTimerEvent::HostActivityChanged)
          holdChanges.onReceive { FluidNotificationTimerEvent.HoldChanged }
          if (timeout != null) timeout.onAwait { it }
        }
      } finally {
        timeout?.cancel()
      }
    }
}

@Composable
fun rememberFluidNotificationHostState(): FluidNotificationHostState =
  remember { FluidNotificationHostState() }

/** Lets any feature publish into the single root host without owning or duplicating its UI. */
val LocalFluidNotificationHostState: ProvidableCompositionLocal<FluidNotificationHostState?> =
  staticCompositionLocalOf { null }

/**
 * A top-of-app notice inspired by iOS banners, hosted outside navigation content.
 *
 * La card si tocca (2.12.0): con un dito sopra il tempo di lettura si ferma, tirata in giu' si apre
 * (testo intero e azioni, senza scadenza), spinta o lanciata in su se ne va. Da TalkBack le stesse
 * cose sono le azioni "Espandi" e "Chiudi". Il dettaglio sta in `FluidNotificationDragState`.
 */
@Composable
fun FluidNotificationHost(
  state: FluidNotificationHostState,
  modifier: Modifier = Modifier,
  backdrop: GlassBackdropState? = null,
) {
  val accessibilityManager by rememberUpdatedState(LocalAccessibilityManager.current)
  val lifecycleOwner = LocalLifecycleOwner.current
  val lifecycleState by lifecycleOwner.lifecycle.currentStateAsState()
  val isWindowFocused = LocalWindowInfo.current.isWindowFocused
  val isHostActive = lifecycleState.isAtLeast(Lifecycle.State.RESUMED) && isWindowFocused
  val presentation = state.presentation
  val visibilityState = remember(state) { MutableTransitionState(false) }
  var laidOutNotificationId by remember(state) { mutableStateOf<String?>(null) }
  // La card lanciata via se ne e' gia' andata, dissolvendosi: la sua uscita di ordinanza sarebbe
  // una scena vuota che tiene in coda la notifica dopo.
  var thrownNotificationId by remember(state) { mutableStateOf<String?>(null) }

  DisposableEffect(state, isHostActive) {
    state.setHostActive(isHostActive)
    onDispose {
      if (isHostActive) state.setHostActive(false)
    }
  }

  LaunchedEffect(
    state,
    isHostActive,
    presentation.notification?.id,
    presentation.visible,
    laidOutNotificationId,
  ) {
    val notificationId = presentation.notification?.id
    if (isHostActive && presentation.visible && laidOutNotificationId == notificationId) {
      notificationId?.let(state::markPresented)
    }
    if (!presentation.visible) laidOutNotificationId = null
  }

  LaunchedEffect(presentation.visible) {
    if (presentation.visible) thrownNotificationId = null
    visibilityState.targetState = presentation.visible
  }

  LaunchedEffect(
    presentation.notification?.id,
    presentation.visible,
    visibilityState.currentState,
    visibilityState.targetState,
    visibilityState.isIdle,
  ) {
    val notificationId = presentation.notification?.id
    if (
      notificationId != null &&
      !presentation.visible &&
      visibilityState.isIdle &&
      !visibilityState.currentState &&
      !visibilityState.targetState
    ) {
      state.markExitCompleted(notificationId)
    }
  }

  LaunchedEffect(state) {
    state.runQueue(
      recommendedTimeout = { notification ->
        accessibilityManager?.calculateRecommendedTimeoutMillis(
          originalTimeoutMillis = notification.durationMillis,
          containsIcons = true,
          containsText = true,
          containsControls = true,
        ) ?: notification.durationMillis
      },
    )
  }

  Box(
    modifier = modifier
      .fillMaxWidth()
      .statusBarsPadding()
      .padding(horizontal = 12.dp, vertical = 8.dp),
    contentAlignment = Alignment.TopCenter,
  ) {
    AnimatedVisibility(
      visibleState = visibilityState,
      enter = slideInVertically(
        initialOffsetY = { -it / 3 },
        animationSpec = FluidMotion.intOffset(
          dampingRatio = FluidMotion.DampingChrome,
          stiffness = FluidMotion.ResponseSnappy,
        ),
      ) + fadeIn(animationSpec = FluidMotion.fadeIn(150)) + scaleIn(
        initialScale = 0.97f,
        transformOrigin = TransformOrigin(0.5f, 0f),
        animationSpec = FluidMotion.snappy(),
      ),
      exit = if (thrownNotificationId != null && thrownNotificationId == presentation.notification?.id) {
        ExitTransition.None
      } else {
        slideOutVertically(
          targetOffsetY = { -it / 4 },
          animationSpec = FluidMotion.intOffset(
            dampingRatio = FluidMotion.DampingChrome,
            stiffness = FluidMotion.ResponseSnappy,
          ),
        ) + fadeOut(animationSpec = FluidMotion.fadeOut(NotificationExitMillis)) + scaleOut(
          targetScale = 0.985f,
          transformOrigin = TransformOrigin(0.5f, 0f),
          animationSpec = FluidMotion.snappy(),
        )
      },
    ) {
      presentation.notification?.let { notification ->
        FluidNotificationCard(
          notification = notification,
          onDismiss = state::dismissCurrent,
          backdrop = backdrop,
          onTouchChanged = { touched -> state.setTouched(notification.id, touched) },
          onExpandedChanged = { expanded -> state.setExpanded(notification.id, expanded) },
          onThrown = { thrownNotificationId = notification.id },
          modifier = Modifier.onGloballyPositioned {
            if (
              state.presentation.visible &&
              state.presentation.notification?.id == notification.id
            ) {
              laidOutNotificationId = notification.id
            }
          },
        )
      }
    }
  }
}

@Immutable
internal data class FluidNotificationIconColors(
  val container: Color,
  val content: Color,
)

internal fun fluidNotificationIconColors(
  tone: FluidNotificationTone,
  colorScheme: ColorScheme,
): FluidNotificationIconColors = when (tone) {
  FluidNotificationTone.Info -> FluidNotificationIconColors(
    container = colorScheme.primary,
    content = colorScheme.onPrimary,
  )
  FluidNotificationTone.Success -> Color(0xFF28A745).let { accent ->
    FluidNotificationIconColors(accent, highestContrastNotificationContent(accent))
  }
  FluidNotificationTone.Warning -> Color(0xFFFF9500).let { accent ->
    FluidNotificationIconColors(accent, highestContrastNotificationContent(accent))
  }
  FluidNotificationTone.Error -> FluidNotificationIconColors(
    container = colorScheme.error,
    content = colorScheme.onError,
  )
}

// -------------------------------------------------------------------------------------------------
// Il dito sulla card (2.12.0)
// -------------------------------------------------------------------------------------------------
//
// Una notifica in-app e' un oggetto che si tocca: la prima cosa che uno fa con un banner e' metterci
// un dito sopra. Fino alla 2.11.0 quel dito non cambiava niente — il conto alla rovescia correva
// sotto di lui e la card se ne andava mentre la si leggeva. Adesso:
//
//  * **un dito sopra ferma il conto**, e al rilascio riparte da quello che restava (mai da meno di
//    un secondo e mezzo): vive in `FluidNotificationHostState`;
//  * **tirata in giu'** la card segue il dito con resistenza (la curva elastica del resto
//    dell'engine) e si allunga un poco; rilasciata oltre 72 dp, o con una spinta verso il basso, si
//    apre — testo intero e azioni — e da aperta non scade piu';
//  * **spinta in su** segue il dito uno a uno (sta andando dove vuole andare); oltre 40 dp, o con
//    una spinta, viene lanciata via e la notifica si chiude;
//  * altrimenti torna dov'era con una molla senza rimbalzi.
//
// Le decisioni sono funzioni pure qui sotto, provate in `FluidNotificationGestureTest`: il dito si
// muove in dp e in dp/s, e un numero sbagliato si vede subito su un telefono e mai in una revisione.

/** Cosa fare quando il dito lascia la card. */
internal enum class FluidNotificationRelease {
  /** Aprirla: testo intero, azioni, niente scadenza. */
  Expand,

  /** Lanciarla via e chiudere la notifica. */
  Dismiss,

  /** Rimetterla dov'era. */
  SpringBack,
}

/** Quanto si tira in giu' (il dito, non la card) perche' un rilascio apra la card. */
internal const val NotificationExpandDistanceDp = 72f

/** Quanto si spinge in su perche' un rilascio lanci via la card. Meno dell'apertura: si butta con meno. */
internal const val NotificationDismissDistanceDp = 40f

/** Da qui un rilascio e' una spinta e conta piu' di dove sta la card. */
internal const val NotificationFlingVelocityDpPerSecond = 1_000f

/** Quanto corre almeno una card lanciata via, anche da un dito lento: deve avere dove andare mentre sfuma. */
internal const val NotificationThrowMinVelocityDpPerSecond = 900f

/**
 * L'asintoto della curva elastica in giu', in dp. Fisso e non ricavato dalla finestra come nel resto
 * dell'engine: qui ad essere tirata e' una card di 72 dp, e la corsa che le sta bene non cresce con
 * lo schermo. A 160 dp la card ha percorso circa 32 dp quando il dito ne ha fatti 72.
 */
internal const val NotificationPullDimensionDp = 160f

/** Di quanto si allunga la card per ogni px di spostamento: poco, e' un accenno di "c'e' altro". */
internal const val NotificationStretchFraction = 0.3f

/** Il tetto dell'allungamento: oltre non e' piu' un accenno, e' una card che cambia misura. */
internal const val NotificationStretchMaxDp = 14f

/**
 * Isteresi del tick aptico sulla soglia di apertura. Un dito fermo sulla soglia trema di un paio
 * di dp, e senza questo margine ogni tremito sarebbe un tick.
 */
internal const val NotificationTickHysteresisDp = 6f

/** La dissolvenza di una card lanciata via: abbastanza da vederla andare, non da farla indugiare. */
internal const val NotificationThrowFadeMillis = 200

/** La dissolvenza con le animazioni ridotte: c'e', ma non si nota. */
internal const val NotificationReducedFadeMillis = 100

/** Sotto questa opacita' il contenuto non si disegna affatto: vedi la seconda trappola in `regole.md`. */
internal const val NotificationGateAlpha = 0.004f

/**
 * Cosa succede al rilascio, dato dove sta il dito e quanto veloce va.
 *
 * @param travelDp quanto il dito ha percorso dal riposo, in dp; positivo e' verso il basso.
 * @param velocityDpPerSecond la velocita' al rilascio, in dp/s; positiva e' verso il basso.
 * @param expanded la card e' gia' aperta: tirarla ancora non la apre di nuovo, torna.
 *
 * Una spinta batte la distanza — e' quello che il dito stava dicendo un istante fa — con una
 * sola eccezione: chi aveva gia' passato la soglia di una parte e rilascia con una spinta
 * dall'altra sta ripensandoci, e la card torna. Lanciare via una notifica perche' si e' tirata in
 * giu' troppo e si e' tornati su di scatto sarebbe la peggiore delle sorprese.
 */
internal fun fluidNotificationRelease(
  travelDp: Float,
  velocityDpPerSecond: Float,
  expanded: Boolean = false,
): FluidNotificationRelease {
  val flungUp = velocityDpPerSecond <= -NotificationFlingVelocityDpPerSecond
  val flungDown = velocityDpPerSecond >= NotificationFlingVelocityDpPerSecond
  val open = if (expanded) FluidNotificationRelease.SpringBack else FluidNotificationRelease.Expand
  return when {
    flungUp && travelDp >= NotificationExpandDistanceDp -> FluidNotificationRelease.SpringBack
    flungDown && travelDp <= -NotificationDismissDistanceDp -> FluidNotificationRelease.SpringBack
    flungUp -> FluidNotificationRelease.Dismiss
    flungDown -> open
    travelDp <= -NotificationDismissDistanceDp -> FluidNotificationRelease.Dismiss
    travelDp >= NotificationExpandDistanceDp -> open
    else -> FluidNotificationRelease.SpringBack
  }
}

/**
 * Dove sta la card quando il dito e' [rawPx] dal riposo. In su e' uno a uno; in giu' passa per la
 * curva elastica di `FluidScrollPhysics`, la stessa dei bordi di ogni lista.
 */
internal fun fluidNotificationOffsetPx(rawPx: Float, dimensionPx: Float): Float =
  if (rawPx >= 0f) rubberBandTranslation(rawPx, dimensionPx) else rawPx

/** L'inverso: dov'e' il dito se la card sta a [offsetPx]. Serve a riprendere una card in volo. */
internal fun fluidNotificationRawPx(offsetPx: Float, dimensionPx: Float): Float =
  if (offsetPx >= 0f) {
    inverseRubberBandTranslation(offsetPx, dimensionPx).coerceAtMost(dimensionPx * 8f)
  } else {
    offsetPx
  }

/**
 * Quanto la card segue il dito, da 0 a 1, a [rawPx] di distanza. Serve a dare alla molla di ritorno
 * la velocita' *della card* e non quella del dito: in giu' le due differiscono per la resistenza, e
 * una molla che parte piu' veloce dell'oggetto che muove fa uno scatto al rilascio.
 */
internal fun fluidNotificationFollow(rawPx: Float, dimensionPx: Float): Float {
  if (rawPx < 0f) return 1f
  val c = OverscrollResistance
  val d = dimensionPx.coerceAtLeast(1f)
  val denominator = d + c * rawPx
  return d * d * c / (denominator * denominator)
}

/**
 * La velocita' con cui la molla di ritorno parte, data quella della card al rilascio.
 *
 * Verso il riposo non puo' essere qualunque. Una molla critica arriva a zero senza oltrepassarlo
 * solo se parte con meno di `omega * distanza`; una card staccata a 10 dp con il dito che la
 * rimandava giu' a 1000 dp/s ne faceva 12 oltre il riposo, un rimbalzo di contenitore che nessuna
 * smorzatura "quasi critica" nasconde. La velocita' in eccesso la assorbe l'attrito del dito che
 * lascia. Allontanandosi dal riposo non serve limite: e' la molla stessa a frenare e riportare.
 */
internal fun fluidNotificationReturnVelocity(offsetPx: Float, cardVelocityPx: Float): Float {
  if (offsetPx == 0f || cardVelocityPx * offsetPx >= 0f) return cardVelocityPx
  val ceiling = NotificationSpringOmega * abs(offsetPx)
  return if (abs(cardVelocityPx) > ceiling) {
    -sign(offsetPx) * ceiling
  } else {
    cardVelocityPx
  }
}

/** La pulsazione della molla di ritorno: la rigidezza e' `omega^2`, con massa uno. */
private val NotificationSpringOmega: Float = sqrt(FluidMotion.ResponseSnappy)

/** Di quanto cresce la card in altezza, in px, quando e' [offsetPx] sotto il riposo. Zero se ridotto. */
internal fun fluidNotificationStretchPx(offsetPx: Float, density: Float, reducedMotion: Boolean): Float {
  if (reducedMotion || offsetPx <= 0f) return 0f
  return minOf(offsetPx * NotificationStretchFraction, NotificationStretchMaxDp * density)
}

/** Se l'apertura e' armata a [travelDp]: con isteresi, per non tremare sulla soglia. */
internal fun fluidNotificationArmed(wasArmed: Boolean, travelDp: Float): Boolean =
  if (wasArmed) {
    travelDp >= NotificationExpandDistanceDp - NotificationTickHysteresisDp
  } else {
    travelDp >= NotificationExpandDistanceDp
  }

/**
 * La molla del ritorno: quasi critica, e non per pigrizia.
 *
 * Una card e' un contenitore, e un contenitore che rimbalza dice "il layout e' sciolto". A 0.9 resta
 * l'ombra di un assestamento — la parte di sensazione fisica che serve — e l'oltrepasso, misurato in
 * `FluidNotificationGestureTest` per ogni rilascio che torna indietro, resta sotto il dp.
 */
internal fun notificationSpringBack(): SpringSpec<Float> = spring(
  dampingRatio = FluidMotion.DampingStandard,
  stiffness = FluidMotion.ResponseSnappy,
  visibilityThreshold = 0.5f,
)

/**
 * Lo stato del dito su una card: dove sta, quanto e' opaca, se e' aperta.
 *
 * Tutto cio' che cambia durante il gesto e' letto nel layer, nel layout o nel disegno, mai nella
 * composizione: tirare una card non ricompone niente. La dissolvenza e lo spostamento stanno su un
 * `graphicsLayer` *fuori* dal vetro, e il vetro li vede come vede una pagina intera che scorre: la
 * card si muove, il fondale che campiona no.
 */
@Stable
internal class FluidNotificationDragState(private val scope: CoroutineScope) {

  /** Spostamento verticale della card dal riposo, in px; positivo e' verso il basso. */
  var offsetPx by mutableFloatStateOf(0f)
    private set

  var alpha by mutableFloatStateOf(1f)
    private set

  var expanded by mutableStateOf(false)
    private set

  /** Lanciata via: da qui in poi la card non risponde piu' al dito. Non torna. */
  var thrown by mutableStateOf(false)
    private set

  val draggableState = DraggableState { delta -> onDelta(delta) }

  private var rawPx = 0f
  private var armed = false
  private var settle: Job? = null

  private var density = 1f
  private var reducedMotion = false
  private var haptics: FluidHaptics = NoOpFluidHaptics
  private var onDismiss: () -> Unit = {}
  private var onExpandedChanged: (Boolean) -> Unit = {}
  private var onThrown: () -> Unit = {}

  private val dimensionPx: Float get() = NotificationPullDimensionDp * density

  /** Di quanto la card e' piu' alta del suo contenuto, mentre la si tira. Letto dal layout. */
  val stretchPx: Float get() = fluidNotificationStretchPx(offsetPx, density, reducedMotion)

  fun update(
    density: Float,
    haptics: FluidHaptics,
    reducedMotion: Boolean,
    onDismiss: () -> Unit,
    onExpandedChanged: (Boolean) -> Unit,
    onThrown: () -> Unit,
  ) {
    this.density = density
    this.haptics = haptics
    this.reducedMotion = reducedMotion
    this.onDismiss = onDismiss
    this.onExpandedChanged = onExpandedChanged
    this.onThrown = onThrown
  }

  /**
   * Il dito ha superato lo slop. Se la card stava ancora tornando la si riprende da dove e': il
   * dito ricomincia da li', non da un riposo che non ha ancora raggiunto.
   */
  fun onDragStarted() {
    if (thrown) return
    settle?.cancel()
    settle = null
    rawPx = fluidNotificationRawPx(offsetPx, dimensionPx)
    armed = !expanded && fluidNotificationArmed(false, rawPx / density)
  }

  fun onDelta(delta: Float) {
    if (thrown) return
    rawPx += delta
    offsetPx = fluidNotificationOffsetPx(rawPx, dimensionPx)
    if (expanded) return
    val nowArmed = fluidNotificationArmed(armed, rawPx / density)
    if (nowArmed != armed) {
      armed = nowArmed
      haptics.play(FluidHapticEvent.Tick)
    }
  }

  fun onDragStopped(velocityPx: Float) {
    if (thrown) return
    when (fluidNotificationRelease(rawPx / density, velocityPx / density, expanded)) {
      FluidNotificationRelease.Expand -> {
        expand()
        springBack(velocityPx)
      }
      FluidNotificationRelease.Dismiss -> throwAway(velocityPx)
      FluidNotificationRelease.SpringBack -> springBack(velocityPx)
    }
  }

  /** Apre la card: dal dito e da TalkBack ("Espandi"). Idempotente. */
  fun expand() {
    if (expanded || thrown) return
    expanded = true
    onExpandedChanged(true)
  }

  private fun springBack(fingerVelocityPx: Float) {
    settle?.cancel()
    val velocity = fluidNotificationReturnVelocity(
      offsetPx = offsetPx,
      cardVelocityPx = fingerVelocityPx * fluidNotificationFollow(rawPx, dimensionPx),
    )
    settle = scope.launch {
      animate(
        initialValue = offsetPx,
        targetValue = 0f,
        initialVelocity = velocity,
        animationSpec = notificationSpringBack(),
      ) { value, _ -> offsetPx = value }
      offsetPx = 0f
    }
  }

  /**
   * La card continua verso l'alto con la velocita' del rilascio mentre sfuma, e a dissolvenza finita
   * la notifica si chiude.
   *
   * Si muove e sfuma, non si rimpicciolisce: scalare il layer di un vetro scala anche il fondale
   * che campiona, e la pagina dietro sembrerebbe muoversi. E la chiusura aspetta che sia
   * invisibile: l'uscita di ordinanza della notifica (scorrimento, scala, dissolvenza) rigiocherebbe
   * il commiato su una card che e' gia' andata.
   */
  private fun throwAway(fingerVelocityPx: Float) {
    thrown = true
    settle?.cancel()
    haptics.play(FluidHapticEvent.Threshold)
    onThrown()
    val upwardPx = -maxOf(-fingerVelocityPx, NotificationThrowMinVelocityDpPerSecond * density)
    settle = scope.launch {
      if (reducedMotion) {
        animate(
          initialValue = alpha,
          targetValue = 0f,
          animationSpec = FluidMotion.fadeOut<Float>(NotificationReducedFadeMillis),
        ) { value, _ -> alpha = value }
      } else {
        coroutineScope {
          val flight = launch {
            animateDecay(
              initialValue = offsetPx,
              initialVelocity = upwardPx,
              animationSpec = FloatExponentialDecaySpec(),
            ) { value, _ -> offsetPx = value }
          }
          animate(
            initialValue = alpha,
            targetValue = 0f,
            animationSpec = FluidMotion.fadeOut<Float>(NotificationThrowFadeMillis),
          ) { value, _ -> alpha = value }
          flight.cancel()
        }
      }
      alpha = 0f
      onDismiss()
    }
  }
}

/**
 * Sa quando un dito e' sulla card, e non la tocca: il gesto resta di chi lo prende. Legge nel
 * passaggio Initial, prima di tutti, cosi' il conto si ferma anche se un tasto della card si
 * prende il tocco, e vede il rilascio anche quando il trascinamento ha consumato il gesto.
 */
@Composable
private fun Modifier.fluidNotificationTouch(onTouchChanged: (Boolean) -> Unit): Modifier {
  val current by rememberUpdatedState(onTouchChanged)
  return pointerInput(Unit) {
    awaitEachGesture {
      awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
      current(true)
      try {
        do {
          val event = awaitPointerEvent(PointerEventPass.Initial)
        } while (event.changes.fastAny { it.pressed })
      } finally {
        current(false)
      }
    }
  }
}

/**
 * Allunga il contenuto di [extraPx] in fondo. E' il *layout* che cambia, non un layer: la card ha una
 * misura sua, il vetro la segue e ricampiona quello che ha dietro. Letto nella misura, quindi tirare
 * rimisura la card e basta, senza ricomporre.
 */
private fun Modifier.fluidNotificationStretch(extraPx: () -> Float): Modifier =
  layout { measurable, constraints ->
    val placeable = measurable.measure(constraints)
    val extra = extraPx().toInt().coerceAtLeast(0)
    layout(placeable.width, placeable.height + extra) { placeable.place(0, 0) }
  }

@Composable
private fun FluidNotificationCard(
  notification: FluidNotification,
  onDismiss: () -> Unit,
  modifier: Modifier = Modifier,
  backdrop: GlassBackdropState? = null,
  onTouchChanged: (Boolean) -> Unit = {},
  onExpandedChanged: (Boolean) -> Unit = {},
  onThrown: () -> Unit = {},
) {
  val iconColors = fluidNotificationIconColors(notification.tone, MaterialTheme.colorScheme)
  val accent = iconColors.container
  val icon = when (notification.tone) {
    FluidNotificationTone.Info -> Icons.Rounded.Info
    FluidNotificationTone.Success -> Icons.Rounded.CheckCircle
    FluidNotificationTone.Warning -> Icons.Rounded.WarningAmber
    FluidNotificationTone.Error -> Icons.Rounded.Error
  }
  val surface = lerp(
    MaterialTheme.colorScheme.surfaceContainerHigh,
    accent,
    // Asked of the theme, not of a colour. `surface` in an app built on this design is a
    // *translucent film* — white at a tenth over a dark page — and `luminance()` ignores alpha,
    // so it answered "light" for the blackest configuration there is and this branch was dead.
    if (GlassDefaults.isDarkSurface()) 0.09f else 0.045f,
  )

  val shape = FluidGlassRoundedShape(20.dp)
  // The card publishes its own material so the close button standing on it refracts the card and
  // not the page behind it — see LocalGlassBackdrop.
  val cardGlass = rememberGlassBackdrop()
  val controlBackdrop = if (backdrop != null) {
    rememberCombinedGlassBackdrop(backdrop, cardGlass)
  } else {
    null
  }
  val glassModifier = if (backdrop != null) {
    Modifier.glassSurface(
      state = backdrop,
      tint = GlassDefaults.modalTint(),
      shape = shape,
      role = GlassRole.Modal,
      exports = cardGlass,
    )
  } else {
    Modifier
  }

  val scope = rememberCoroutineScope()
  val haptics = rememberFluidHaptics()
  val reducedMotion = LocalFluidMotionPolicy.current.reducedMotion
  val density = LocalDensity.current.density
  // Per notifica: un'altra card, un altro dito. Lo stato non passa da una all'altra.
  val drag = remember(notification.id, scope) { FluidNotificationDragState(scope) }
  SideEffect {
    drag.update(
      density = density,
      haptics = haptics,
      reducedMotion = reducedMotion,
      onDismiss = onDismiss,
      onExpandedChanged = onExpandedChanged,
      onThrown = onThrown,
    )
  }
  val expanded = drag.expanded
  val showActions = expanded && notification.actions.isNotEmpty()

  Surface(
    modifier = modifier
      .fillMaxWidth()
      .widthIn(max = 560.dp)
      .fluidNotificationTouch(onTouchChanged)
      // Il trascinamento sta *fuori* dal layer che muove la card, come nel foglio del modale: il
      // riferimento del dito non si muove con l'oggetto che il dito sposta.
      .draggable(
        state = drag.draggableState,
        orientation = Orientation.Vertical,
        enabled = !drag.thrown,
        onDragStarted = { drag.onDragStarted() },
        onDragStopped = { velocity -> drag.onDragStopped(velocity) },
      )
      // Traslazione e opacita', mai scala: vedi "tre trappole del vetro in movimento".
      .graphicsLayer {
        translationY = drag.offsetPx
        alpha = drag.alpha
      }
      // Il primo e l'ultimo fotogramma di vita di un nodo possono uscire con le proprieta' di
      // default, cioe' opaco e al suo posto: una card lanciata via e appena tolta ricomparirebbe
      // un istante, intera, dov'era. A opacita' quasi zero non si registra niente.
      .drawWithContent { if (drag.alpha > NotificationGateAlpha) drawContent() }
      .then(glassModifier)
      .semantics {
        paneTitle = "Notifica in-app"
        liveRegion = LiveRegionMode.Polite
        // Il trascinamento non e' per tutti: le stesse due cose si fanno anche da TalkBack.
        customActions = buildList {
          if (!drag.expanded) add(CustomAccessibilityAction("Espandi") { drag.expand(); true })
          add(CustomAccessibilityAction("Chiudi") { onDismiss(); true })
        }
      },
    shape = shape,
    color = if (backdrop == null) surface else Color.Transparent,
    contentColor = MaterialTheme.colorScheme.onSurface,
    border = if (backdrop == null) BorderStroke(0.5.dp, accent.copy(alpha = 0.22f)) else null,
    shadowElevation = 12.dp,
  ) {
    CompositionLocalProvider(LocalGlassBackdrop provides controlBackdrop) {
      Column(
        modifier = Modifier
          // L'allungamento del dito sta fuori da `animateContentSize`: e' il dito a guidarlo, e
          // passato per una molla seguirebbe con un ritardo che si leggerebbe come elasticita'.
          .fluidNotificationStretch { drag.stretchPx }
          // L'apertura cambia l'altezza del contenitore, e il vetro la segue: e' layout, non scala.
          // Critica, perche' un contenitore non rimbalza. Il ritaglio comprende i bordi della card,
          // cosi' la crescita dei tasti non viene tagliata a filo.
          .animateContentSize(
            animationSpec = FluidMotion.intSize(
              dampingRatio = FluidMotion.DampingChrome,
              stiffness = FluidMotion.ResponseStandard,
            ),
          ),
      ) {
        Row(
          modifier = Modifier.padding(
            start = 14.dp,
            top = 12.dp,
            end = 6.dp,
            bottom = if (showActions) 0.dp else 12.dp,
          ),
          horizontalArrangement = Arrangement.spacedBy(12.dp),
          // In alto, e non al centro: a card aperta il testo cresce, e l'icona e il tasto devono
          // restare dove l'occhio li cerca. Per una riga sola i 5 dp sopra e sotto rifanno
          // esattamente il centro di prima (tasto da 48, tessera da 38).
          verticalAlignment = Alignment.Top,
        ) {
          Box(
            modifier = Modifier
              .padding(top = 5.dp)
              .size(38.dp)
              .background(iconColors.container, ContinuousCornerShape(11.dp)),
            contentAlignment = Alignment.Center,
          ) {
            Icon(icon, contentDescription = null, tint = iconColors.content, modifier = Modifier.size(21.dp))
          }
          Column(
            modifier = Modifier
              .weight(1f)
              .padding(vertical = 5.dp),
            verticalArrangement = Arrangement.spacedBy(2.dp),
          ) {
            Text(
              text = notification.title,
              style = MaterialTheme.typography.titleSmall,
              fontWeight = FontWeight.SemiBold,
            )
            Text(
              text = notification.message,
              style = MaterialTheme.typography.bodySmall,
              color = MaterialTheme.colorScheme.onSurfaceVariant,
              // Chiusa: due righe, e il resto si apre tirando. Aperta: tutto.
              maxLines = if (expanded) Int.MAX_VALUE else NotificationCollapsedLines,
              overflow = TextOverflow.Ellipsis,
            )
          }
          FluidGlassIconButton(onClick = onDismiss) {
            Icon(
              imageVector = Icons.Rounded.Close,
              contentDescription = "Chiudi notifica",
              tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
          }
        }
        if (showActions) {
          Row(
            // Allineate al testo (14 + 38 + 12), non all'icona: sono cose che si fanno *su* quel testo.
            modifier = Modifier.padding(start = 64.dp, end = 14.dp, top = 4.dp, bottom = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
          ) {
            notification.actions.forEach { action ->
              FluidGlassButton(
                text = action.label,
                onClick = {
                  action.onClick()
                  onDismiss()
                },
              )
            }
          }
        }
      }
    }
  }
}

private fun highestContrastNotificationContent(background: Color): Color {
  val dark = Color(0xFF121214)
  val light = Color(0xFFFDFDFF)
  fun ratio(foreground: Color): Float {
    val high = maxOf(foreground.luminance(), background.luminance())
    val low = minOf(foreground.luminance(), background.luminance())
    return (high + 0.05f) / (low + 0.05f)
  }
  return if (ratio(dark) >= ratio(light)) dark else light
}

private const val NotificationExitMillis = 180

/** Le righe del messaggio a card chiusa. */
private const val NotificationCollapsedLines = 2
