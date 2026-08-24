package dev.antigravity.fluidengine.ui.fluid

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.MutableTransitionState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
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
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.ProvidableCompositionLocal
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalAccessibilityManager
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.paneTitle
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.currentStateAsState
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.async
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.selects.select
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

enum class FluidNotificationTone {
  Info,
  Success,
  Warning,
  Error,
}

@Immutable
data class FluidNotification(
  val id: String,
  val title: String,
  val message: String,
  val tone: FluidNotificationTone = FluidNotificationTone.Info,
  val durationMillis: Long = 6_000L,
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
  data object TimedOut : FluidNotificationTimerEvent
}

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
  @Volatile private var isHostActive = false
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
      // Payload and visibility change atomically, so the previous FIFO item cannot re-enter for
      // a frame while a LaunchedEffect catches up with the new notification.
      presentation = FluidNotificationPresentation(notification = notification, visible = true)
      // markPresented is accepted only when the card has layout, the lifecycle is RESUMED and the
      // window owns focus. The reading budget below is then counted only during the same state.
      request.presented.await()
      val timeout = recommendedTimeout(notification).coerceAtLeast(1_500L)
      awaitDismissalOrActiveTimeout(notification.id, timeout, monotonicTimeMillis)
      presentation = FluidNotificationPresentation(notification = notification, visible = false)
      // A duration constant cannot track a spring (or the system animator scale). Waiting for the
      // UI handshake keeps the outgoing payload intact until every exit channel is actually idle.
      request.exitCompleted.await()
    } finally {
      if (currentRequest === request) currentRequest = null
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
    while (remainingMillis > 0L) {
      while (!isHostActive) {
        hostActivityChanges.receive()
      }

      val activeStartedAt = monotonicTimeMillis()
      val event = awaitTimerEvent(remainingMillis)
      val activeElapsed = (monotonicTimeMillis() - activeStartedAt)
        .coerceIn(0L, remainingMillis)
      remainingMillis -= activeElapsed

      when (event) {
        is FluidNotificationTimerEvent.Dismissed -> if (event.id == notificationId) return
        is FluidNotificationTimerEvent.HostActivityChanged -> Unit
        FluidNotificationTimerEvent.TimedOut -> return
      }
    }
  }

  private suspend fun awaitTimerEvent(remainingMillis: Long): FluidNotificationTimerEvent =
    coroutineScope {
      val timeout = async {
        delay(remainingMillis)
        FluidNotificationTimerEvent.TimedOut
      }
      try {
        select {
          dismissals.onReceive(FluidNotificationTimerEvent::Dismissed)
          hostActivityChanges.onReceive(FluidNotificationTimerEvent::HostActivityChanged)
          timeout.onAwait { it }
        }
      } finally {
        timeout.cancel()
      }
    }
}

@Composable
fun rememberFluidNotificationHostState(): FluidNotificationHostState =
  remember { FluidNotificationHostState() }

/** Lets any feature publish into the single root host without owning or duplicating its UI. */
val LocalFluidNotificationHostState: ProvidableCompositionLocal<FluidNotificationHostState?> =
  staticCompositionLocalOf { null }

/** A top-of-app notice inspired by iOS banners, hosted outside navigation content. */
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
      exit = slideOutVertically(
        targetOffsetY = { -it / 4 },
        animationSpec = FluidMotion.intOffset(
          dampingRatio = FluidMotion.DampingChrome,
          stiffness = FluidMotion.ResponseSnappy,
        ),
      ) + fadeOut(animationSpec = FluidMotion.fadeOut(NotificationExitMillis)) + scaleOut(
        targetScale = 0.985f,
        transformOrigin = TransformOrigin(0.5f, 0f),
        animationSpec = FluidMotion.snappy(),
      ),
    ) {
      presentation.notification?.let { notification ->
        FluidNotificationCard(
          notification = notification,
          onDismiss = state::dismissCurrent,
          backdrop = backdrop,
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

@Composable
private fun FluidNotificationCard(
  notification: FluidNotification,
  onDismiss: () -> Unit,
  modifier: Modifier = Modifier,
  backdrop: GlassBackdropState? = null,
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
    if (MaterialTheme.colorScheme.surface.luminance() < 0.5f) 0.09f else 0.045f,
  )

  val shape = ContinuousCornerShape(20.dp)
  val glassModifier = if (backdrop != null) {
    Modifier.glassSurface(
      state = backdrop,
      tint = GlassDefaults.floatingTint(),
      shape = shape,
      role = GlassRole.Modal,
    )
  } else {
    Modifier
  }

  Surface(
    modifier = modifier
      .fillMaxWidth()
      .widthIn(max = 560.dp)
      .then(glassModifier)
      .semantics {
        paneTitle = "Notifica in-app"
        liveRegion = LiveRegionMode.Polite
      },
    shape = shape,
    color = if (backdrop == null) surface else Color.Transparent,
    contentColor = MaterialTheme.colorScheme.onSurface,
    border = if (backdrop == null) BorderStroke(0.5.dp, accent.copy(alpha = 0.22f)) else null,
    shadowElevation = 12.dp,
  ) {
    Row(
      modifier = Modifier.padding(start = 14.dp, top = 12.dp, bottom = 12.dp, end = 6.dp),
      horizontalArrangement = Arrangement.spacedBy(12.dp),
      verticalAlignment = Alignment.CenterVertically,
    ) {
      Box(
        modifier = Modifier
          .size(38.dp)
          .background(iconColors.container, ContinuousCornerShape(11.dp)),
        contentAlignment = Alignment.Center,
      ) {
        Icon(icon, contentDescription = null, tint = iconColors.content, modifier = Modifier.size(21.dp))
      }
      Column(
        modifier = Modifier.weight(1f),
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
