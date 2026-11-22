package dev.antigravity.fluidengine.ui.fluid.viewer

import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.util.VelocityTracker
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.onGloballyPositioned
import dev.antigravity.fluidengine.ui.fluid.FluidZoomState
import dev.antigravity.fluidengine.ui.page.SharedPageSpace

/** MyCloud-Gallery-2 ViewerDismissSpec and ViewerGestureArbiter, 2026-11-22. */
internal object ViewerPinch {
  const val SlopShare = .5f
  const val DismissScale = .85f
  const val FlingRate = 1.6f
  fun scale(raw: Float) = if (raw <= 1f) raw.coerceAtLeast(.25f) else (1f + (raw - 1f) * .35f).coerceAtMost(1.15f)
  fun dismiss(scale: Float, rate: Float) = rate < FlingRate && (scale <= DismissScale || rate <= -FlingRate)
}

/** Measure in the stationary host, even when the hit node is shrinking below the fingers. */
@Composable
internal fun Modifier.viewerPinchDismiss(space: SharedPageSpace?, zoom: FluidZoomState, enabled: Boolean): Modifier {
  if (space == null || !enabled) return this
  val host = space.host
  var coordinates by remember { mutableStateOf<LayoutCoordinates?>(null) }
  return onGloballyPositioned { coordinates = it }.pointerInput(space, zoom) {
    awaitEachGesture {
      val down = awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
      if (zoom.isZoomed || !host.canDismissDown) return@awaitEachGesture
      var span = 0f
      var anchor = Offset.Zero
      var scale = 1f
      var token: Int? = null
      val velocity = VelocityTracker()
      try {
        do {
          val event = awaitPointerEvent(PointerEventPass.Initial)
          val fingers = event.changes.count { it.pressed }
          if (fingers != 2) {
            if (span == 0f && fingers == 1) {
              if ((event.changes.first { it.pressed }.position - down.position).getDistance() > viewConfiguration.touchSlop) break
              continue
            }
            val current = token ?: break
            if (fingers < 2) {
              velocity.addPosition(event.changes.first().uptimeMillis, Offset(scale, 0f))
              val rate = velocity.calculateVelocity().x
              val speed = (if (scale > 1f) rate else -rate) * host.height
              host.releaseVertical(current, speed, cancelled = event.changes.any { it.isConsumed },
                decision = ViewerPinch.dismiss(scale, rate))
              token = null
              event.changes.forEach { it.consume() }
            }
            break
          }
          if (token == null && zoom.isZoomed) break
          val local = coordinates?.takeIf { it.isAttached } ?: break
          val root = host.hostCoordinates?.takeIf { it.isAttached } ?: break
          val first = event.changes.first { it.pressed }
          val a = root.localPositionOf(local, first.position)
          val b = root.localPositionOf(local, event.changes.last { it.pressed }.position)
          val radius = (a - b).getDistance() / 2f
          val center = (a + b) / 2f
          if (span == 0f) {
            span = radius
            anchor = center
          }
          if (span <= 0f) continue
          val delta = radius - span
          if (token == null && delta > viewConfiguration.touchSlop * ViewerPinch.SlopShare) break
          val raw = radius / span
          scale = ViewerPinch.scale(raw)
          velocity.addPosition(first.uptimeMillis, Offset(scale, 0f))
          if (token == null && delta <= -viewConfiguration.touchSlop * ViewerPinch.SlopShare)
            token = host.beginVertical(null) ?: break
          token?.let { host.movePinch(it, scale, anchor, center) }
          if (token != null) event.changes.forEach { it.consume() }
        } while (event.changes.any { it.pressed })
      } finally {
        token?.let { host.releaseVertical(it, 0f, cancelled = true) }
      }
    }
  }
}
