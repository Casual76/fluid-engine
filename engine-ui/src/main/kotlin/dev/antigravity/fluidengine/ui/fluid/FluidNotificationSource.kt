package dev.antigravity.fluidengine.ui.fluid

import android.os.SystemClock
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.withInfiniteAnimationFrameNanos
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathMeasure
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.dp
import dev.antigravity.fluidengine.ui.fluidphysics.FluidCornerRadii
import dev.antigravity.fluidengine.ui.fluidphysics.FluidForm
import dev.antigravity.fluidengine.ui.fluidphysics.FluidPhysicsTier
import dev.antigravity.fluidengine.ui.fluidphysics.fluidPhysicsShadow
import dev.antigravity.fluidengine.ui.fluidphysics.fluidPhysicsSurfaceInked
import dev.antigravity.fluidengine.ui.fluidphysics.rememberFluidPhysicsState
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.selects.select
import kotlin.math.roundToInt

/**
 * Finestra mostrata dall'anello, sullo stesso orologio monotono di [SystemClock.elapsedRealtime].
 * L'anello non cambia né il timeout di lettura, né la disponibilità dell'azione. L'app può togliere
 * l'azione con [FluidNotificationHostState.updateCurrent] e deve verificare la scadenza nel callback.
 */
@Immutable
data class FluidNotificationCountdown(
  val durationMillis: Long,
  val startedAtElapsedRealtimeMillis: Long = SystemClock.elapsedRealtime(),
) {
  init {
    require(durationMillis > 0L) { "Il conto alla rovescia richiede una durata positiva" }
    require(startedAtElapsedRealtimeMillis >= 0L) { "L'istante di partenza deve usare l'orologio monotono" }
  }

  internal fun fractionAt(nowMillis: Long): Float =
    1f - (nowMillis - startedAtElapsedRealtimeMillis).coerceIn(0L, durationMillis).toFloat() / durationMillis
}

/** Conserva il punto di partenza anche mentre la richiesta aspetta in coda. */
internal class FluidNotificationSource private constructor(
  val handle: FluidTransitionSource,
  val initial: FluidTransitionSourceGeometry,
) {
  companion object {
    fun capture(handle: FluidTransitionSource): FluidNotificationSource? =
      handle.resolve()?.let { FluidNotificationSource(handle, it) }
  }

  fun canReturn(): Boolean = handle.resolve() != null
}

internal class FluidNotificationFlightContent(
  val contentModifier: Modifier,
  val exports: GlassBackdropState,
  val ink: GlassInk,
  val drag: FluidNotificationDragState,
  val returnToSource: () -> Boolean,
)

/** In uno spazio stretto il pannello cede prima l'allineamento, poi la distanza dalla sorgente. */
internal fun fluidNotificationSourceFrame(anchor: Rect, card: Size, room: Size, margin: Float, gap: Float): Rect {
  val minX = margin.coerceAtMost((room.width - card.width).coerceAtLeast(0f) / 2f)
  val minY = margin.coerceAtMost((room.height - card.height).coerceAtLeast(0f) / 2f)
  val left = (anchor.center.x - card.width / 2f).coerceIn(minX, maxOf(minX, room.width - card.width - minX))
  val nearEdge = if (anchor.center.y <= room.height / 2f) anchor.bottom + gap else anchor.top - gap - card.height
  val top = nearEdge.coerceIn(minY, maxOf(minY, room.height - card.height - minY))
  return Rect(Offset(left.roundToInt().toFloat(), top.roundToInt().toFloat()), card)
}

// Posa accanto alla barra, a occhio, 21 novembre 2026 (scelta di Alessio).
private val NotificationSourceGap = 10.dp
private val NotificationSourceMargin = 12.dp

@Composable
internal fun FluidNotificationSourceCard(
  source: FluidNotificationSource,
  notification: FluidNotification,
  visible: Boolean,
  hostActive: Boolean,
  backdrop: GlassBackdropState?,
  modifier: Modifier,
  onPresented: () -> Unit,
  onExit: () -> Unit,
  onDismiss: () -> Unit,
  onAction: (FluidNotificationAction) -> Unit,
  onTouchChanged: (Boolean) -> Unit,
  onExpandedChanged: (Boolean) -> Unit,
) {
  val reduced = LocalFluidMotionPolicy.current.reducedMotion
  val journey = remember(source) { FluidSourceJourney(source.handle, source.initial, FluidModalSourceMotion.Popover) }
  val physics = rememberFluidPhysicsState(FluidForm.Slab(Rect.Zero, FluidCornerRadii.all(0f)))
  val lens = rememberFluidModalLens(!reduced)
  val exports = rememberGlassBackdrop()
  val ink = rememberGlassInk()
  val emptyBackdrop = rememberEmptyGlassBackdrop()
  val opts = GlassDefaults.panelOptics(overSharpPage = true)
  val palette = MaterialTheme.colorScheme
  val accent = fluidNotificationIconColors(notification.tone, palette).container
  val tint = if (backdrop != null) {
    GlassDefaults.panelTint()
  } else {
    val opaque = fluidNotificationSurfaceColor(accent)
    GlassTint(opaque, opaque, accent.copy(alpha = 0.22f))
  }
  val scope = rememberCoroutineScope()
  val drag = remember(source, scope) { FluidNotificationDragState(scope) }
  var thrownAway by remember(source) { mutableStateOf(false) }
  var rootOrigin by remember { mutableStateOf<Offset?>(null) }
  var pane by remember(source) { mutableStateOf(Rect.Zero) }
  var stageStarted by remember(source) { mutableStateOf(false) }
  val exitAlpha = remember(source) { Animatable(1f) }
  val exitSlide = remember(source) { Animatable(0f) }
  val onPresentedNow by rememberUpdatedState(onPresented)
  val onExitNow by rememberUpdatedState(onExit)

  // Aspetta il primo layout: la goccia e il tasto devono usare la stessa radice dal primo fotogramma.
  val ready by remember(source) { derivedStateOf { !pane.isEmpty } }
  val interactive by remember(source, visible) {
    derivedStateOf { visible && journey.focus.value > 0f && !pane.isEmpty }
  }
  val settled by remember(source) { derivedStateOf { ready && journey.focus.value == 1f && journey.clock.value == 1f } }
  DisposableEffect(journey, ready) {
    if (ready) {
      journey.takeOver(source.handle.acquireForModal())
      // Il primo layout può precedere la presa: il corpo entra solo insieme al passaggio di disegno.
      stageStarted = true
    }
    onDispose {
      stageStarted = false
      journey.release()
    }
  }
  LaunchedEffect(journey, ready, visible) {
    if (ready && visible && !journey.hasLease) {
      snapshotFlow { source.canReturn() }.first { it }
      journey.takeOver(source.handle.acquireForModal())
    }
  }
  LaunchedEffect(journey, ready, visible, reduced) {
    if (visible) {
      if (ready) {
        journey.retarget(visible = true)
        journey.run(reduced)
      }
    } else {
      if (ready && !thrownAway && !reduced) {
        val returned = source.canReturn() && journey.returnWhilePresent(source)
        if (!returned) {
          // Stessa scivolata dell'avviso di serie. Se il tasto esce durante il ritorno, riparte dalla sagoma corrente.
          coroutineScope {
            launch { exitAlpha.animateTo(0f, FluidMotion.fadeOut(NotificationExitMillis)) }
            exitSlide.animateTo(-pane.height / 4f, FluidMotion.pixels(
              dampingRatio = FluidMotion.DampingChrome, stiffness = FluidMotion.ResponseSnappy,
            ))
          }
        }
      }
      onExitNow()
    }
  }
  LaunchedEffect(journey, settled, hostActive, visible) {
    if (hostActive && visible && settled) onPresentedNow()
  }

  val progress = { journey.clock.value }
  val bodyAlpha = {
    if (!stageStarted) 0f else journey.bodyAlpha(progress(), physics) * drag.alpha * exitAlpha.value
  }
  val contentModifier = Modifier
    .graphicsLayer {
      val round = fluidModalTravelClip(physics, pane, progress(), journey.focus.value)
      clip = round != null
      if (round != null) shape = FluidPopoverTravelClipShape(round)
    }
    .drawWithContent {
      if (stageStarted && journey.contentAlpha(progress()) * exitAlpha.value > NotificationGateAlpha) drawContent()
    }
    .graphicsLayer {
      val p = progress()
      val focus = journey.focus.value
      val map = fluidModalContentMap(physics, pane, p, focus)
      alpha = journey.contentAlpha(p) * exitAlpha.value * (ink.state?.inkAlpha() ?: 1f)
      translationX = map.translation.x
      translationY = map.translation.y
      scaleX = map.scale
      scaleY = map.scale
      renderEffect = fluidModalLensEffect(lens, physics, pane, map, focus)
    }

  Layout(
    modifier = modifier.fillMaxSize().windowInsetsPadding(WindowInsets.safeDrawing).imePadding()
      .onGloballyPositioned { rootOrigin = it.positionInRoot() },
    content = {
      val radiusPx = with(LocalDensity.current) { FluidNotificationRadius.toPx() }
      FluidMenuReturnSurface(journey.controlledReturn) {
        Modifier.drawWithContent { if (bodyAlpha() > NotificationGateAlpha) drawContent() }
          .fluidPhysicsShadow(physics, opts, { pane.size }, radiusPx, amount = bodyAlpha)
          .fluidPhysicsSurfaceInked(
            ink = ink,
            state = physics,
            backdrop = backdrop ?: emptyBackdrop,
            tint = tint,
            role = GlassRole.Modal,
            optics = opts,
            tier = if (backdrop == null) FluidPhysicsTier.Lite else null,
            intensity = bodyAlpha,
            exports = exports.takeIf { backdrop != null },
          )
      }
      journey.SourceFace(physics, progress)
      FluidNotificationCard(
        notification = notification,
        onDismiss = onDismiss,
        onAction = onAction,
        onTouchChanged = onTouchChanged,
        onExpandedChanged = onExpandedChanged,
        onThrown = { thrownAway = true },
        backdrop = backdrop,
        flight = FluidNotificationFlightContent(contentModifier, exports, ink, drag, source::canReturn),
        interactive = interactive,
      )
    },
  ) { children, constraints ->
    val width = constraints.maxWidth
    val height = constraints.maxHeight
    val stage = Constraints.fixed(width, height)
    val body = children[0].measure(stage)
    val face = children[1].measure(stage)
    val availableWidth = (width - NotificationSourceMargin.roundToPx() * 2).coerceAtLeast(0)
    val cardWidth = minOf(FluidNotificationMaxWidth.roundToPx(), availableWidth)
    val card = children[2].measure(Constraints(minWidth = cardWidth, maxWidth = cardWidth, maxHeight = height))
    val origin = rootOrigin
    layout(width, height) {
      if (origin != null && card.width > 0 && card.height > 0) {
        journey.rootOrigin = origin
        val resting = fluidNotificationSourceFrame(
          source.initial.bounds.translate(-origin.x, -origin.y),
          Size(card.width.toFloat(), card.height.toFloat()), Size(width.toFloat(), height.toFloat()),
          NotificationSourceMargin.toPx(), NotificationSourceGap.toPx(),
        )
        pane = resting.translate(0f, drag.offsetPx)
        physics.density = density
        journey.drive(physics, pane, FluidNotificationRadius.toPx(), width.toFloat(), this@Layout, layoutDirection, progress)
        val slide = exitSlide.value.roundToInt()
        body.place(0, slide)
        face.place(0, slide)
        card.place(resting.left.roundToInt(), resting.top.roundToInt() + slide)
      }
    }
  }
}

/** Se il tasto esce anche a ritorno iniziato, l'altra uscita riparte dalla stessa sagoma. */
private suspend fun FluidSourceJourney.returnWhilePresent(source: FluidNotificationSource): Boolean = coroutineScope {
  val completed = async { retarget(visible = false); run(reduced = false) }
  val detached = async { snapshotFlow { source.canReturn() }.first { !it } }
  try {
    select {
      completed.onAwait { true }
      detached.onAwait { false }
    }
  } finally {
    completed.cancel()
    detached.cancel()
  }
}

/** Segue il perimetro della capsula, dalle ore dodici, fino a svuotarsi. */
@Composable
internal fun FluidNotificationCountdownButton(
  action: FluidNotificationAction,
  countdown: FluidNotificationCountdown,
  enabled: Boolean,
  onClick: () -> Unit,
) {
  val reduced = LocalFluidMotionPolicy.current.reducedMotion
  val progress = remember(countdown) { mutableFloatStateOf(countdown.fractionAt(SystemClock.elapsedRealtime())) }
  val tint = MaterialTheme.colorScheme.primary
  LaunchedEffect(countdown, reduced) {
    while (progress.floatValue > 0f) {
      // A movimento ridotto cambia solo al secondo. Altrimenti segue i fotogrammi senza ricomporre.
      if (reduced) delay(1_000L) else withInfiniteAnimationFrameNanos { }
      progress.floatValue = countdown.fractionAt(SystemClock.elapsedRealtime())
    }
  }
  FluidGlassButton(
    text = action.label,
    onClick = onClick,
    enabled = enabled,
    sizing = FluidButtonSizing.Fitted,
    modifier = Modifier.heightIn(min = 48.dp).drawWithCache {
      val stroke = Stroke(width = 2.dp.toPx(), cap = StrokeCap.Round)
      val inset = 3.dp.toPx()
      val radius = (size.height / 2f - inset).coerceAtMost(size.width / 2f - inset)
      if (radius <= 0f) return@drawWithCache onDrawWithContent { drawContent() }
      val diameter = 2 * radius
      val endX = size.width - inset
      val endY = size.height - inset
      val capsule = Path().apply {
        moveTo(size.width / 2f, inset)
        arcTo(Rect(endX - diameter, inset, endX, inset + diameter), -90f, 90f, false)
        arcTo(Rect(endX - diameter, endY - diameter, endX, endY), 0f, 90f, false)
        arcTo(Rect(inset, endY - diameter, inset + diameter, endY), 90f, 90f, false)
        arcTo(Rect(inset, inset, inset + diameter, inset + diameter), 180f, 90f, false)
        close()
      }
      val perimeter = PathMeasure().apply { setPath(capsule, forceClosed = true) }
      val remaining = Path()
      onDrawWithContent {
        drawContent()
        val fraction = progress.floatValue
        if (fraction > 0f) {
          remaining.reset()
          perimeter.getSegment(0f, perimeter.length * fraction, remaining, startWithMoveTo = true)
          drawPath(remaining, tint, style = stroke)
        }
      }
    },
  )
}
