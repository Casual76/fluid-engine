package dev.antigravity.fluidengine.viewer.scene

import android.content.Context
import android.util.Log
import android.view.Choreographer
import android.view.GestureDetector
import android.view.MotionEvent
import android.view.Surface
import android.view.TextureView
import android.view.ViewConfiguration
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.unit.IntSize
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
/**
 * F6C.20 · La diagnosi della superficie sul dispositivo: spenta di serie, si accende con
 * `adb shell setprop log.tag.FluidScene3d DEBUG`. Poche righe per sessione, mai una per fotogramma.
 */
internal object SceneTrace {
  private const val Tag = "FluidScene3d"
  fun log(message: () -> String) {
    if (Log.isLoggable(Tag, Log.DEBUG)) Log.d(Tag, message())
  }
}

/**
 * F6C.20 · Le misure del viewport. [UiHelper] di Filament, attaccato a una [TextureView] che ha già la sua superficie,
 * annuncia la superficie con le misure «desiderate», che non diamo: 0×0. Succede nella galleria, dove l'entrata dalla
 * miniatura registra la pagina (e fa nascere la superficie) prima che la pagina diventi attiva e la sessione parta: il
 * viewport restava vuoto, Filament disegnava un fotogramma trasparente e lo contava come primo, e la pagina restava
 * bianca anche sotto il dito. Le misure che mancano si prendono dalla vista.
 */
internal object SceneViewport {
  /** Le misure annunciate, o quelle della vista se le annunciate sono nulle; null se non ce ne sono ancora. */
  fun of(reportedWidth: Int, reportedHeight: Int, viewWidth: Int, viewHeight: Int): IntSize? = when {
    reportedWidth > 0 && reportedHeight > 0 -> IntSize(reportedWidth, reportedHeight)
    viewWidth > 0 && viewHeight > 0 -> IntSize(viewWidth, viewHeight)
    else -> null
  }
}

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

/**
 * F6C.20 · Ciò che la pagina del modello chiede alla sua superficie a ogni ricomposizione: se disegnare, la clip e se
 * suona, la richiesta di inquadratura e la rotazione automatica. La superficie vera è [SceneTexture] (Filament); le
 * prove JVM, dove Filament non c'è, ne danno una che ricorda gli ordini.
 */
internal interface SceneSurface {
  fun update(active: Boolean, play: Boolean, animation: Int, frameRequest: Int, autoRotate: Boolean)
  fun dispose()
}

/** F6C.20 · Crea la vista della superficie ([SceneSurface]) per la pagina del modello. */
internal fun interface SceneSurfaceFactory {
  fun create(context: Context, model: FluidSceneModel, onFirstFrame: () -> Unit, onError: (Throwable) -> Unit): android.view.View
}

/** La superficie di serie: Filament su una [TextureView]. */
internal val FilamentSceneSurface = SceneSurfaceFactory { context, model, onFirstFrame, onError ->
  SceneTexture(context, model, onFirstFrame, onError)
}

internal class SceneTexture(
  context: Context,
  private val model: FluidSceneModel,
  private val onFirstFrame: () -> Unit,
  private val onError: (Throwable) -> Unit,
) : TextureView(context), SceneSurface {
  private var session: OrbitSession? = null
  private var enabledForPage = false
  private var playing = false
  private var clip = 0
  private var reset = 0
  private var rotate = false
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
    SceneTrace.log { "vista attaccata ${System.identityHashCode(this)}, pagina attiva $enabledForPage" }
    updateSession()
  }

  override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
    super.onSizeChanged(w, h, oldw, oldh)
    session?.viewResized(w, h)
  }

  override fun onDetachedFromWindow() {
    SceneTrace.log { "vista staccata ${System.identityHashCode(this)}, sessione ${session != null}" }
    destroySession()
    super.onDetachedFromWindow()
  }

  override fun update(active: Boolean, play: Boolean, animation: Int, frameRequest: Int, autoRotate: Boolean) {
    if (enabledForPage != active) SceneTrace.log { "pagina attiva $active (${System.identityHashCode(this)})" }
    enabledForPage = active
    playing = play
    clip = animation
    rotate = autoRotate
    updateSession()
    val renderer = session ?: return
    renderer.play(play, animation)
    renderer.autoRotate(autoRotate)
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
        SceneTrace.log { "sessione creata (${System.identityHashCode(this)}) ${width}x$height, disponibile $isAvailable" }
        session = OrbitSession.create(this, model, onFirstFrame).also {
          it.play(playing, clip)
          it.autoRotate(rotate)
        }
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

  override fun dispose() {
    released = true
    destroySession()
  }

  private fun destroySession() {
    if (session != null) SceneTrace.log { "sessione chiusa (${System.identityHashCode(this)})" }
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
  private val onFirstFrame: () -> Unit,
) : Closeable, Choreographer.FrameCallback, UiHelper.RendererCallback {
  private val manager = EntityManager.get()
  private val renderer = engine.createRenderer()
  private val scene = engine.createScene()
  private val view = engine.createView()
  // R2: il modello si carica prima di creare le entità. Se Filament lo rifiuta, `create` distrugge l'engine, che porta
  // con sé renderer, scena, vista e luce; le entità di `EntityManager` sono del processo e resterebbero orfane.
  private val loaded = model.load(engine)
  private val cameraEntity = manager.create()
  private val camera = engine.createCamera(cameraEntity)
  private val sun = manager.create()
  private val ui = UiHelper(UiHelper.ContextErrorPolicy.DONT_CHECK)
  private val display = DisplayHelper(texture.context)
  private val frames = Choreographer.getInstance()
  private val lighting = KTX1Loader.createIndirectLight(engine, texture.assetBuffer(StudioAsset))
  private val ground: ShadowGround
  private val orbit = Manipulator.Builder().viewport(1, 1).panning(true)
    .targetPosition(0f, 0f, 0f).orbitHomePosition(OrbitProfile.EyeX, OrbitProfile.EyeY, OrbitProfile.EyeZ)
    .fovDegrees(OrbitProfile.Fov.toFloat()).build(Manipulator.Mode.ORBIT)
  private val eye = DoubleArray(3)
  private val target = DoubleArray(3)
  private val upward = DoubleArray(3)
  private var swap: SwapChain? = null
  // F6C.20: le misure date al viewport. Sopra `init`: attachTo può annunciare la superficie già nel costruttore.
  private var applied: IntSize? = null
  private var posted = false
  private var closed = false
  private var resourceProgress = 0f
  private var firstFrameSent = false
  private var playing = false
  private var animation = 0
  private var poseDirty = false
  private var time = 0f
  private var lastFrame = 0L
  // F6C.20: la rotazione automatica ([SceneSpin]): l'angolo sopra l'inquadratura del manipolatore, ferma sotto il dito.
  private var spinning = false
  private var spin = 0.0
  private var spinFrame = 0L
  private var grabbing = false
  private var spinResumeAt = 0L
  var active = true
    set(value) {
      if (field == value) return
      field = value
      lastFrame = 0L
      spinFrame = 0L
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

  /** F6C.20 · Accende o spegne la rotazione automatica; accesa, gira da subito. */
  fun autoRotate(on: Boolean) {
    if (spinning == on) return
    spinning = on
    spinFrame = 0L
    spinResumeAt = 0L
    if (on) requestFrame()
  }

  fun frameModel() {
    orbit.jumpToBookmark(orbit.homeBookmark)
    // «Inquadra» torna alla vista di partenza anche se la camera stava girando da sola.
    spin = 0.0
    spinFrame = 0L
    requestFrame()
  }

  fun beginGrab(position: Offset, pan: Boolean) {
    grabbing = true
    orbit.grabBegin(position.x.toInt(), position.y.toInt(), pan)
  }

  fun grab(position: Offset) {
    orbit.grabUpdate(position.x.toInt(), position.y.toInt())
    requestFrame()
  }

  fun endGrab() {
    orbit.grabEnd()
    if (grabbing) spinResumeAt = System.nanoTime() + SceneSpin.ResumeDelayNanos
    grabbing = false
  }

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
    // Choreographer dà il tempo del fotogramma nella stessa base di System.nanoTime(), come la ripresa dopo il dito.
    val turning = spinning && !grabbing && frameTimeNanos >= spinResumeAt
    if (turning && spinFrame != 0L) spin = SceneSpin.advance(spin, frameTimeNanos - spinFrame)
    spinFrame = if (turning) frameTimeNanos else 0L
    orbit.getLookAt(eye, target, upward)
    SceneSpin.turn(spin, eye, target, upward)
    camera.lookAt(eye[0], eye[1], eye[2], target[0], target[1], target[2], upward[0], upward[1], upward[2])
    val drawn = renderer.beginFrame(chain, frameTimeNanos)
    if (drawn) {
      renderer.render(view)
      renderer.endFrame()
      if (!firstFrameSent && resourceProgress >= 1f) {
        firstFrameSent = true
        SceneTrace.log { "primo fotogramma, ${texture.width}x${texture.height}, visibile ${texture.isShown}" }
        onFirstFrame()
      }
    }
    if (!drawn || playing || spinning || resourceProgress < 1f) requestFrame()
  }

  override fun onNativeWindowChanged(surface: Surface) {
    SceneTrace.log { "superficie collegata, ${texture.width}x${texture.height}" }
    swap?.let(engine::destroySwapChain)
    swap = engine.createSwapChain(surface, ui.swapChainFlags)
    // F6C.20: senza un viewport Filament disegna un fotogramma vuoto e lo conta come primo (la pagina resta bianca).
    if (applied == null) SceneViewport.of(0, 0, texture.width, texture.height)?.let(::resize)
    texture.display?.let { display.attach(renderer, it) }
    requestFrame()
  }

  override fun onDetachedFromSurface() {
    SceneTrace.log { "superficie staccata" }
    display.detach()
    val previous = swap ?: return
    swap = null
    engine.destroySwapChain(previous)
    engine.flushAndWait()
  }

  override fun onResized(width: Int, height: Int) {
    SceneTrace.log { "misure ${width}x$height, vista ${texture.width}x${texture.height}" }
    resize(SceneViewport.of(width, height, texture.width, texture.height) ?: return)
  }

  /** F6C.20 · La vista ha cambiato misura: il viewport la segue anche se [UiHelper] non l'ha detto. */
  fun viewResized(width: Int, height: Int) {
    if (applied == null || SceneViewport.of(0, 0, width, height) == applied) return
    resize(SceneViewport.of(0, 0, width, height) ?: return)
  }

  private fun resize(size: IntSize) {
    applied = size
    val width = size.width
    val height = size.height
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
    fun create(texture: TextureView, model: FluidSceneModel, onFirstFrame: () -> Unit): OrbitSession {
      Utils.init()
      val engine = Engine.create()
      return try {
        OrbitSession(texture, engine, model, onFirstFrame)
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
