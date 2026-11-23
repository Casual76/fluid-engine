package dev.antigravity.fluidengine.viewer.ar

import android.content.Context
import android.util.Log
import android.view.Choreographer
import android.view.GestureDetector
import android.view.MotionEvent
import android.view.Surface
import android.view.TextureView
import androidx.annotation.RequiresApi
import androidx.compose.ui.geometry.Offset
import com.google.android.filament.android.UiHelper
import com.google.ar.core.Config
import com.google.ar.core.Plane
import com.google.ar.core.Session
import com.google.ar.core.TrackingState
import dev.antigravity.fluidengine.viewer.scene.FluidSceneModel
import kotlin.math.atan2

@RequiresApi(27)
internal class ArTexture(
  context: Context,
  private val model: FluidSceneModel,
  private val onState: (ArRoomState) -> Unit,
  private val onImageChanged: () -> Unit,
  private val onFailure: () -> Unit,
) : TextureView(context), Choreographer.FrameCallback, UiHelper.RendererCallback {
  private val choreographer = Choreographer.getInstance()
  private val ui = UiHelper(UiHelper.ContextErrorPolicy.DONT_CHECK)
  private var ar: Session? = null
  private var render: ArRenderSession? = null
  private var active = false
  private var resumed = false
  private var failed = false
  private var released = false
  private var viewportWidth = 0
  private var viewportHeight = 0
  private var displayRotation = -1
  private var pendingTap: Offset? = null
  private var pendingDrag: ArDrag? = null
  private var previous: Offset? = null
  private var span: Offset? = null
  private var multiTouch = false
  private var lastState = ArRoomState()
  private var hasPlane = false
  private var presentedImage = 0L
  private val taps = GestureDetector(context, object : GestureDetector.SimpleOnGestureListener() {
    override fun onDown(e: MotionEvent) = true
    override fun onSingleTapUp(e: MotionEvent): Boolean {
      if (!multiTouch) pendingTap = e.point()
      return true
    }
  })

  init {
    ui.renderCallback = this
    ui.attachTo(this)
  }

  override fun onAttachedToWindow() {
    super.onAttachedToWindow()
    sync()
  }

  override fun onDetachedFromWindow() {
    pauseTracking()
    super.onDetachedFromWindow()
  }

  fun setActive(active: Boolean) {
    this.active = active
    sync()
  }

  private fun sync() {
    if (released || failed || !active || !isAttachedToWindow || !ui.isReadyToRender || viewportWidth <= 0 || viewportHeight <= 0) {
      pauseTracking()
      return
    }
    guarded {
      val tracking = ar ?: Session(context).also {
        ar = it
        it.configure(Config(it).apply {
          planeFindingMode = Config.PlaneFindingMode.HORIZONTAL
          focusMode = Config.FocusMode.AUTO
          updateMode = Config.UpdateMode.LATEST_CAMERA_IMAGE
          textureUpdateMode = Config.TextureUpdateMode.EXPOSE_HARDWARE_BUFFER
          lightEstimationMode = Config.LightEstimationMode.DISABLED
        })
      }
      if (resumed) return@guarded
      tracking.resume()
      resumed = true
      choreographer.postFrameCallback(this)
    }
  }

  override fun doFrame(frameTimeNanos: Long) {
    val tracking = ar ?: return
    val renderer = render ?: return
    if (!resumed) return
    guarded {
      val rotation = display?.rotation ?: Surface.ROTATION_0
      if (rotation != displayRotation) {
        tracking.setDisplayGeometry(rotation, viewportWidth, viewportHeight)
        displayRotation = rotation
      }
      val frame = tracking.update()
      // Trackables persist across frames, but the plane may have appeared before camera tracking recovered.
      if (!hasPlane) hasPlane = frame.getUpdatedTrackables(Plane::class.java).any {
        it.type == Plane.Type.HORIZONTAL_UPWARD_FACING && it.trackingState == TrackingState.TRACKING
      }
      val state = renderer.draw(frame, frameTimeNanos, hasPlane, pendingTap, pendingDrag)
      if (frame.timestamp != 0L && frame.timestamp != presentedImage) {
        presentedImage = frame.timestamp
        onImageChanged()
      }
      pendingTap = null
      pendingDrag = null
      if (state != lastState) {
        lastState = state
        onState(state)
      }
      choreographer.postFrameCallback(this)
    }
  }

  override fun onNativeWindowChanged(surface: Surface) {
    guarded {
      val renderer = render ?: ArRenderSession.create(context, model).also { render = it }
      renderer.surface(surface)
    }
  }

  override fun onResized(width: Int, height: Int) {
    if (width <= 0 || height <= 0) return
    viewportWidth = width
    viewportHeight = height
    displayRotation = -1
    render?.resize(width, height)
    sync()
  }

  override fun onDetachedFromSurface() {
    pauseTracking()
    render?.detachSurface()
    viewportWidth = 0
    viewportHeight = 0
  }

  fun placeAtCenter() {
    if (lastState.tracking == ArTracking.SurfaceFound) pendingTap = Offset(width / 2f, height / 2f)
  }

  override fun onTouchEvent(event: MotionEvent): Boolean {
    if (!resumed) return false
    if (event.actionMasked == MotionEvent.ACTION_DOWN) {
      multiTouch = false
      previous = event.point()
      span = null
      parent?.requestDisallowInterceptTouchEvent(true)
    } else if (event.actionMasked == MotionEvent.ACTION_POINTER_DOWN) {
      multiTouch = true
      previous = null
      span = event.span()
    }
    taps.onTouchEvent(event)
    when (event.actionMasked) {
      MotionEvent.ACTION_MOVE -> if (event.pointerCount == 2) {
        val next = event.span()
        val before = span
        if (before != null && before.getDistance() >= 1f && next.getDistance() >= 1f) {
          render?.transform(next.getDistance() / before.getDistance(),
            atan2(next.y, next.x) - atan2(before.y, before.x))
        }
        span = next
      } else if (event.pointerCount == 1) {
        val next = event.point()
        if (lastState.tracking == ArTracking.Placed) {
          val start = pendingDrag?.start ?: previous
          if (start != null) pendingDrag = ArDrag(start, next)
        }
        previous = next
      }
      MotionEvent.ACTION_POINTER_UP -> {
        previous = if (event.pointerCount == 2) event.point(1 - event.actionIndex) else null
        span = null
      }
      MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
        previous = null
        span = null
        parent?.requestDisallowInterceptTouchEvent(false)
      }
    }
    return true
  }

  fun dispose() {
    released = true
    try {
      pauseTracking()
      ui.detach()
      render?.close()
    } finally {
      render = null
      val session = ar
      ar = null
      // ARCore's close can take seconds. The view paused it on the UI thread.
      if (session != null) Thread(session::close, "FluidArSessionClose").start()
    }
  }

  private fun pauseTracking() {
    choreographer.removeFrameCallback(this)
    if (!resumed) return
    resumed = false
    pendingTap = null
    pendingDrag = null
    presentedImage = 0L
    try {
      ar?.pause()
    } catch (error: RuntimeException) {
      Log.w(ArLog, "ARCore pause", error)
    }
    render?.clearCameraImage()
  }

  private inline fun guarded(block: () -> Unit) {
    if (released || failed) return
    try {
      block()
    } catch (error: Exception) {
      fail(error)
    } catch (error: LinkageError) {
      fail(error)
    }
  }

  private fun fail(error: Throwable) {
    failed = true
    pauseTracking()
    Log.w(ArLog, "AR camera or Filament", error)
    post { if (!released) onFailure() }
  }

  private fun MotionEvent.point(index: Int = 0) = Offset(getX(index), getY(index))
  private fun MotionEvent.span() = point(1) - point(0)
}
