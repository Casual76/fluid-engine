package dev.antigravity.fluidengine.viewer.scene

import android.content.Context
import android.view.Choreographer
import android.view.GestureDetector
import android.view.MotionEvent
import android.view.Surface
import android.view.TextureView
import android.view.ViewConfiguration
import androidx.compose.ui.geometry.Offset
import com.google.android.filament.Box
import com.google.android.filament.Camera
import com.google.android.filament.Engine
import com.google.android.filament.EntityManager
import com.google.android.filament.IndexBuffer
import com.google.android.filament.LightManager
import com.google.android.filament.Material
import com.google.android.filament.RenderableManager
import com.google.android.filament.Renderer
import com.google.android.filament.SwapChain
import com.google.android.filament.VertexBuffer
import com.google.android.filament.View
import com.google.android.filament.Viewport
import com.google.android.filament.android.DisplayHelper
import com.google.android.filament.android.UiHelper
import com.google.android.filament.utils.KTX1Loader
import com.google.android.filament.utils.Manipulator
import com.google.android.filament.utils.Utils
import java.io.Closeable
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.abs
import kotlin.math.ln

internal const val GroundAsset = "fluid-viewer-3d/ground.filamat"
internal const val StudioAsset = "fluid-viewer-3d/studio.ktx"

/** Distanze e sensibilità a occhio per un modello inquadrato in un cubo di lato 2, 22 novembre 2026. */
private object OrbitProfile {
  const val EyeX = 3.2f
  const val EyeY = 1.95f
  const val EyeZ = 4.5f
  const val Fov = 42.0
  const val Near = 0.02
  const val Far = 64.0
  const val ZoomSensitivity = 18f
  const val PinchLimit = 2.6f
  const val PinchEpsilon = 0.0001f
  const val GroundSize = 6f
}

internal class SceneTexture(
  context: Context,
  private val model: FluidSceneModel,
  private val onError: (Throwable) -> Unit,
) : TextureView(context) {
  private var session: OrbitSession? = null
  private var enabledForPage = false
  private var playing = false
  private var clip = 0
  private var reset = 0
  private var released = false
  private var failed = false
  private var touchMode = 0
  private var pinchSpan = 0f
  private var pinch = 0f
  private val edge = ViewConfiguration.get(context).scaledEdgeSlop
  private val taps = GestureDetector(context, object : GestureDetector.SimpleOnGestureListener() {
    override fun onDown(e: MotionEvent) = true
    override fun onDoubleTap(e: MotionEvent): Boolean {
      pinch = 0f
      session?.frameModel()
      return true
    }
  })

  override fun onAttachedToWindow() {
    super.onAttachedToWindow()
    updateSession()
  }

  override fun onDetachedFromWindow() {
    destroySession()
    super.onDetachedFromWindow()
  }

  fun update(active: Boolean, play: Boolean, animation: Int, frameRequest: Int) {
    enabledForPage = active
    playing = play
    clip = animation
    updateSession()
    val renderer = session ?: return
    renderer.play(play, animation)
    if (reset != frameRequest) {
      pinch = 0f
      renderer.frameModel()
    }
    reset = frameRequest
  }

  private fun updateSession() {
    if (released || failed || !enabledForPage || !isAttachedToWindow) {
      session?.active = false
      return
    }
    if (session == null) {
      try {
        session = OrbitSession.create(this, model).also { it.play(playing, clip) }
      } catch (e: Exception) {
        fail(e)
      } catch (e: LinkageError) {
        fail(e)
      }
    }
    session?.active = true
  }

  private fun fail(error: Throwable) {
    failed = true
    post { if (!released) onError(error) }
  }

  fun dispose() {
    released = true
    destroySession()
  }

  private fun destroySession() {
    session?.close()
    session = null
    touchMode = 0
    pinch = 0f
  }

  override fun onTouchEvent(event: MotionEvent): Boolean {
    val controller = session?.takeIf { enabledForPage } ?: return false
    if (event.actionMasked == MotionEvent.ACTION_DOWN) {
      if (event.x < edge || event.x > width - edge || event.y < edge || event.y > height - edge) return false
      parent?.requestDisallowInterceptTouchEvent(true)
    }
    taps.onTouchEvent(event)
    when (event.actionMasked) {
      MotionEvent.ACTION_DOWN -> start(controller, 1, event.position(0))
      MotionEvent.ACTION_POINTER_DOWN -> if (event.pointerCount == 2) beginPan(controller, event)
      MotionEvent.ACTION_MOVE -> when {
        touchMode == 2 && event.pointerCount >= 2 -> {
          val a = event.position(0)
          val b = event.position(1)
          val center = (a + b) / 2f
          val span = (a - b).getDistance().coerceAtLeast(1f)
          val next = (pinch + ln(span / pinchSpan)).coerceIn(-OrbitProfile.PinchLimit, OrbitProfile.PinchLimit)
          controller.panAndZoom(center, next - pinch)
          pinch = next
          pinchSpan = span
        }
        touchMode == 1 -> controller.grab(event.position(0))
      }
      MotionEvent.ACTION_POINTER_UP -> if (event.pointerCount == 2) start(controller, 1, event.position(1 - event.actionIndex))
      MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
        controller.endGrab()
        touchMode = 0
        parent?.requestDisallowInterceptTouchEvent(false)
      }
    }
    return true
  }

  private fun beginPan(controller: OrbitSession, event: MotionEvent) {
    val a = event.position(0)
    val b = event.position(1)
    pinchSpan = (a - b).getDistance().coerceAtLeast(1f)
    start(controller, 2, (a + b) / 2f)
  }

  private fun start(controller: OrbitSession, mode: Int, position: Offset) {
    if (touchMode != 0) controller.endGrab()
    touchMode = mode
    controller.beginGrab(position, mode == 2)
  }

  private fun MotionEvent.position(index: Int) = Offset(getX(index), height - getY(index))
}

private class OrbitSession private constructor(
  private val texture: TextureView,
  private val engine: Engine,
  model: FluidSceneModel,
) : Closeable, Choreographer.FrameCallback, UiHelper.RendererCallback {
  private val manager = EntityManager.get()
  private val renderer = engine.createRenderer()
  private val scene = engine.createScene()
  private val view = engine.createView()
  private val cameraEntity = manager.create()
  private val camera = engine.createCamera(cameraEntity)
  private val sun = manager.create()
  private val ui = UiHelper(UiHelper.ContextErrorPolicy.DONT_CHECK)
  private val display = DisplayHelper(texture.context)
  private val frames = Choreographer.getInstance()
  private val lighting = KTX1Loader.createIndirectLight(engine, texture.assetBuffer(StudioAsset))
  private val loaded = model.load(engine)
  private val ground: ShadowGround
  private val orbit = Manipulator.Builder().viewport(1, 1).panning(true)
    .targetPosition(0f, 0f, 0f).orbitHomePosition(OrbitProfile.EyeX, OrbitProfile.EyeY, OrbitProfile.EyeZ)
    .fovDegrees(OrbitProfile.Fov.toFloat()).build(Manipulator.Mode.ORBIT)
  private val eye = DoubleArray(3)
  private val target = DoubleArray(3)
  private val upward = DoubleArray(3)
  private var swap: SwapChain? = null
  private var posted = false
  private var closed = false
  private var resourceProgress = 0f
  private var playing = false
  private var animation = 0
  private var poseDirty = false
  private var time = 0f
  private var lastFrame = 0L
  var active = true
    set(value) {
      if (field == value) return
      field = value
      lastFrame = 0L
      if (value) requestFrame()
      else if (posted) {
        frames.removeFrameCallback(this)
        posted = false
      }
    }

  init {
    view.scene = scene
    view.camera = camera
    // Transparent swapchain and view retain the Compose theme around the model.
    view.blendMode = View.BlendMode.TRANSLUCENT
    renderer.clearOptions = Renderer.ClearOptions().apply {
      clear = true
      clearColor = doubleArrayOf(0.0, 0.0, 0.0, 0.0)
    }
    camera.setExposure(16f, 1f / 125f, 100f)
    scene.indirectLight = requireNotNull(lighting.indirectLight).apply { intensity = 27_000f }
    LightManager.Builder(LightManager.Type.DIRECTIONAL).direction(-0.55f, -0.95f, -0.42f)
      .intensity(82_000f).castShadows(true).build(engine, sun)
    scene.addEntity(sun)
    // Keep the source dimensions and coordinates intact for other consumers of FluidSceneModel.
    val bounds = loaded.asset.boundingBox
    val center = bounds.center
    val half = bounds.halfExtent
    val largest = half.maxOrNull() ?: 0f
    val scale = if (largest.isFinite() && largest > 1e-15f) 1f / largest else 1f
    engine.transformManager.let { transforms ->
      transforms.setTransform(transforms.getInstance(loaded.asset.root), floatArrayOf(
        scale, 0f, 0f, 0f, 0f, scale, 0f, 0f, 0f, 0f, scale, 0f,
        -center[0] * scale, -center[1] * scale, -center[2] * scale, 1f,
      ))
    }
    ground = ShadowGround(engine, texture.assetBuffer(GroundAsset), -half[1] * scale - 0.015f)
    scene.addEntity(ground.entity)
    loaded.attach(scene)
    ui.isOpaque = false
    ui.renderCallback = this
    ui.attachTo(texture)
  }

  fun play(play: Boolean, index: Int) {
    if (animation != index) {
      animation = index
      time = 0f
      poseDirty = play
    }
    if (playing != play) {
      playing = play
      lastFrame = 0L
    }
    if (poseDirty || playing) requestFrame()
  }

  fun frameModel() {
    orbit.jumpToBookmark(orbit.homeBookmark)
    requestFrame()
  }

  fun beginGrab(position: Offset, pan: Boolean) = orbit.grabBegin(position.x.toInt(), position.y.toInt(), pan)

  fun grab(position: Offset) {
    orbit.grabUpdate(position.x.toInt(), position.y.toInt())
    requestFrame()
  }

  fun endGrab() = orbit.grabEnd()

  fun panAndZoom(position: Offset, logarithmicDelta: Float) {
    val x = position.x.toInt()
    val y = position.y.toInt()
    orbit.grabUpdate(x, y)
    if (abs(logarithmicDelta) >= OrbitProfile.PinchEpsilon) {
      // The pan restores its starting camera on each update. Rebase it after zooming.
      orbit.grabEnd()
      orbit.scroll(x, y, -logarithmicDelta * OrbitProfile.ZoomSensitivity)
      orbit.grabBegin(x, y, true)
    }
    requestFrame()
  }

  private fun requestFrame() {
    if (closed || !active || posted || swap == null) return
    posted = true
    frames.postFrameCallback(this)
  }

  override fun doFrame(frameTimeNanos: Long) {
    posted = false
    val chain = swap ?: return
    if (closed || !active || !ui.isReadyToRender) return
    if (resourceProgress < 1f) resourceProgress = loaded.updateResources()
    val animator = loaded.asset.instance.animator
    if (animator.animationCount > animation && (poseDirty || playing)) {
      val duration = animator.getAnimationDuration(animation)
      if (playing && lastFrame != 0L && duration > 0f) {
        time = (time + (frameTimeNanos - lastFrame) / 1_000_000_000f) % duration
      }
      animator.applyAnimation(animation, time)
      animator.updateBoneMatrices()
    }
    poseDirty = false
    lastFrame = if (playing) frameTimeNanos else 0L
    orbit.getLookAt(eye, target, upward)
    camera.lookAt(eye[0], eye[1], eye[2], target[0], target[1], target[2], upward[0], upward[1], upward[2])
    val drawn = renderer.beginFrame(chain, frameTimeNanos)
    if (drawn) {
      renderer.render(view)
      renderer.endFrame()
    }
    if (!drawn || playing || resourceProgress < 1f) requestFrame()
  }

  override fun onNativeWindowChanged(surface: Surface) {
    swap?.let(engine::destroySwapChain)
    swap = engine.createSwapChain(surface, ui.swapChainFlags)
    texture.display?.let { display.attach(renderer, it) }
    requestFrame()
  }

  override fun onDetachedFromSurface() {
    display.detach()
    val previous = swap ?: return
    swap = null
    engine.destroySwapChain(previous)
    engine.flushAndWait()
  }

  override fun onResized(width: Int, height: Int) {
    if (width <= 0 || height <= 0) return
    engine.flushAndWait()
    view.viewport = Viewport(0, 0, width, height)
    orbit.setViewport(width, height)
    val aspect = width.toDouble() / height
    camera.setProjection(OrbitProfile.Fov, aspect, OrbitProfile.Near, OrbitProfile.Far,
      if (aspect < 1) Camera.Fov.HORIZONTAL else Camera.Fov.VERTICAL)
    requestFrame()
  }

  override fun close() {
    if (closed) return
    closed = true
    active = false
    ui.detach()
    onDetachedFromSurface()
    loaded.close()
    scene.removeEntity(ground.entity)
    ground.close()
    scene.removeEntity(sun)
    engine.destroyEntity(sun)
    manager.destroy(sun)
    scene.indirectLight = null
    lighting.indirectLight?.let(engine::destroyIndirectLight)
    lighting.cubemap?.let(engine::destroyTexture)
    engine.destroyView(view)
    engine.destroyScene(scene)
    engine.destroyRenderer(renderer)
    engine.destroyCameraComponent(cameraEntity)
    manager.destroy(cameraEntity)
    engine.destroy()
  }

  companion object {
    fun create(texture: TextureView, model: FluidSceneModel): OrbitSession {
      Utils.init()
      val engine = Engine.create()
      return try {
        OrbitSession(texture, engine, model)
      } catch (t: Throwable) {
        engine.destroy()
        throw t
      }
    }
  }
}

private class ShadowGround(private val engine: Engine, materialBytes: ByteBuffer, y: Float) : Closeable {
  val entity: Int = EntityManager.get().create()
  private val material = Material.Builder().payload(materialBytes, materialBytes.remaining()).build(engine)
  private val vertices = VertexBuffer.Builder().vertexCount(4).bufferCount(1)
    .attribute(VertexBuffer.VertexAttribute.POSITION, 0, VertexBuffer.AttributeType.FLOAT3, 0, 12).build(engine)
  private val indices = IndexBuffer.Builder().indexCount(6).bufferType(IndexBuffer.Builder.IndexType.USHORT).build(engine)

  init {
    val s = OrbitProfile.GroundSize
    val vertexBytes = ByteBuffer.allocateDirect(48).order(ByteOrder.nativeOrder()).apply {
      asFloatBuffer().put(floatArrayOf(-s, y, -s, s, y, -s, s, y, s, -s, y, s))
    }
    val indexBytes = ByteBuffer.allocateDirect(12).order(ByteOrder.nativeOrder()).apply {
      asShortBuffer().put(shortArrayOf(0, 2, 1, 0, 3, 2))
    }
    vertices.setBufferAt(engine, 0, vertexBytes)
    indices.setBuffer(engine, indexBytes)
    RenderableManager.Builder(1).boundingBox(Box(0f, y, 0f, s, 0.01f, s))
      .geometry(0, RenderableManager.PrimitiveType.TRIANGLES, vertices, indices)
      .material(0, material.defaultInstance).castShadows(false).receiveShadows(true).build(engine, entity)
  }

  override fun close() {
    engine.destroyEntity(entity)
    EntityManager.get().destroy(entity)
    engine.destroyIndexBuffer(indices)
    engine.destroyVertexBuffer(vertices)
    engine.destroyMaterial(material)
  }
}

private fun TextureView.assetBuffer(name: String) = context.assets.open(name).use { ByteBuffer.wrap(it.readBytes()) }
