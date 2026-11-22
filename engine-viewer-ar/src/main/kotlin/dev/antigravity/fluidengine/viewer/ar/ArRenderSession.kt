package dev.antigravity.fluidengine.viewer.ar

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.view.Surface
import androidx.compose.ui.geometry.Offset
import com.google.android.filament.ColorGrading
import com.google.android.filament.Engine
import com.google.android.filament.EntityManager
import com.google.android.filament.LightManager
import com.google.android.filament.Renderer
import com.google.android.filament.Stream
import com.google.android.filament.SwapChain
import com.google.android.filament.Texture
import com.google.android.filament.TextureSampler
import com.google.android.filament.ToneMapper
import com.google.android.filament.Viewport
import com.google.android.filament.utils.KTX1Loader
import com.google.android.filament.utils.Utils
import com.google.ar.core.Anchor
import com.google.ar.core.Coordinates2d
import com.google.ar.core.Frame
import com.google.ar.core.Plane
import com.google.ar.core.Pose
import com.google.ar.core.TrackingState
import com.google.ar.core.exceptions.NotTrackingException
import com.google.ar.core.exceptions.NotYetAvailableException
import dev.antigravity.fluidengine.viewer.scene.FluidLoadedScene
import dev.antigravity.fluidengine.viewer.scene.FluidSceneModel
import java.io.Closeable
import java.nio.ByteBuffer

internal enum class ArTracking { Starting, Scanning, SurfaceFound, Placed, Lost }

internal data class ArRoomState(val tracking: ArTracking = ArTracking.Starting, val scale: Float = 1f)

@androidx.annotation.RequiresApi(27)
internal class ArRenderSession private constructor(
  context: Context,
  private val engine: Engine,
  private val loaded: FluidLoadedScene,
) : Closeable {
  private val entities = EntityManager.get()
  private val renderer = engine.createRenderer()
  private val scene = engine.createScene()
  private val view = engine.createView()
  private val cameraEntity = entities.create()
  private val camera = engine.createCamera(cameraEntity)
  private val color = ColorGrading.Builder().toneMapper(ToneMapper.Linear()).build(engine)
  private val sun = entities.create()
  private val ambient = context.assets.open("fluid-viewer-3d/studio.ktx").use {
    KTX1Loader.createIndirectLight(engine, ByteBuffer.wrap(it.readBytes()))
  }
  private val video = ArQuad(engine, context, "fluid-viewer-ar/camera.filamat",
    floatArrayOf(-1f, -1f, 1f, 1f, -1f, 1f, 1f, 1f, 1f, -1f, 1f, 1f), screenSpace = true)
  private val shadow = ArQuad(engine, context, "fluid-viewer-3d/ground.filamat",
    floatArrayOf(-1f, 0f, 1f, 1f, 0f, 1f, 1f, 0f, -1f, -1f, 0f, -1f), screenSpace = false)
  private val stream = Stream.Builder().build(engine)
  private val videoTexture = Texture.Builder().sampler(Texture.Sampler.SAMPLER_EXTERNAL)
    .format(Texture.InternalFormat.RGB8).build(engine)
  private val releases = Handler(Looper.getMainLooper())
  private val placement = loaded.asset.boundingBox.let { ArPlacement(it.center, it.halfExtent) }
  private val ndc = floatArrayOf(-1f, -1f, 1f, -1f, 1f, 1f, -1f, 1f)
  private val uv = FloatArray(8)
  private val projection = FloatArray(16)
  private val projectionDouble = DoubleArray(16)
  private val transform = FloatArray(16)
  private var width = 0
  private var height = 0
  private var swap: SwapChain? = null
  private var anchor: Anchor? = null
  private var modelShown = false
  private var videoShown = false
  private var timestamp = 0L
  private var resources = 0f
  private var updateUv = true

  init {
    view.scene = scene
    view.camera = camera
    view.colorGrading = color
    renderer.clearOptions = Renderer.ClearOptions().apply { clear = true }
    camera.setExposure(16f, 1f / 125f, 100f)
    scene.indirectLight = requireNotNull(ambient.indirectLight).apply { intensity = 27_000f }
    LightManager.Builder(LightManager.Type.DIRECTIONAL).direction(-0.50f, -0.90f, -0.36f)
      .intensity(65_000f).castShadows(true).build(engine, sun)
    scene.addEntity(sun)
    videoTexture.setExternalStream(engine, stream)
    video.material.defaultInstance.setParameter("cameraFeed", videoTexture, TextureSampler(
      TextureSampler.MinFilter.LINEAR, TextureSampler.MagFilter.LINEAR, TextureSampler.WrapMode.CLAMP_TO_EDGE))
  }

  fun surface(surface: Surface) {
    detachSurface()
    swap = engine.createSwapChain(surface)
  }

  fun detachSurface() {
    val chain = swap ?: return
    swap = null
    engine.destroySwapChain(chain)
    engine.flushAndWait()
  }

  fun resize(width: Int, height: Int) {
    this.width = width
    this.height = height
    view.viewport = Viewport(0, 0, width, height)
    updateUv = true
  }

  fun clearCameraImage() {
    if (videoShown) scene.removeEntity(video.entity)
    videoShown = false
    timestamp = 0L
  }

  fun draw(frame: Frame, frameTime: Long, hasPlane: Boolean, place: Offset?, drag: ArDrag?): ArRoomState {
    val chain = swap ?: return ArRoomState()
    val arCamera = frame.camera
    val tracking = arCamera.trackingState == TrackingState.TRACKING
    val pose = arCamera.displayOrientedPose
    arCamera.getProjectionMatrix(projection, 0, Near.toFloat(), Far.toFloat())
    projection.forEachIndexed { index, value -> projectionDouble[index] = value.toDouble() }
    camera.setCustomProjection(projectionDouble, Near, Far)
    pose.toMatrix(transform, 0)
    camera.setModelMatrix(transform)

    if (resources < 1f) resources = loaded.updateResources()
    if (anchor?.trackingState == TrackingState.STOPPED) {
      anchor?.detach()
      anchor = null
      placement.reset()
    }
    if (tracking && anchor == null && place != null) {
      anchor = try {
        frame.hitTest(place.x, place.y).firstOrNull { hit ->
          val plane = hit.trackable as? Plane ?: return@firstOrNull false
          plane.trackingState == TrackingState.TRACKING && plane.type == Plane.Type.HORIZONTAL_UPWARD_FACING &&
            plane.isPoseInPolygon(hit.hitPose)
        }?.createAnchor()
      } catch (_: NotTrackingException) {
        null
      }
    }
    val trackedAnchor = anchor?.takeIf { tracking && it.trackingState == TrackingState.TRACKING }
    if (trackedAnchor != null) {
      val translation = trackedAnchor.pose.translation
      if (drag != null) {
        val start = planePoint(pose, drag.start, translation[1])
        val end = planePoint(pose, drag.end, translation[1])
        if (start != null && end != null) placement.move(end[0] - start[0], end[1] - start[1])
      }
      val transforms = engine.transformManager
      placement.transformModel(translation, transform)
      transforms.setTransform(transforms.getInstance(loaded.asset.root), transform)
      placement.transformShadow(translation, transform)
      transforms.setTransform(transforms.getInstance(shadow.entity), transform)
    }
    showModel(trackedAnchor != null)

    if (updateUv || frame.hasDisplayGeometryChanged()) {
      frame.transformCoordinates2d(Coordinates2d.OPENGL_NORMALIZED_DEVICE_COORDINATES, ndc,
        Coordinates2d.TEXTURE_NORMALIZED, uv)
      video.setUv(uv)
      updateUv = false
    }
    if (frame.timestamp != 0L && frame.timestamp != timestamp) acquireImage(frame)
    if (renderer.beginFrame(chain, frameTime)) {
      try {
        renderer.render(view)
      } finally {
        renderer.endFrame()
      }
    }
    val status = when {
      !videoShown -> ArTracking.Starting
      trackedAnchor != null -> ArTracking.Placed
      anchor != null -> ArTracking.Lost
      tracking && hasPlane -> ArTracking.SurfaceFound
      else -> ArTracking.Scanning
    }
    return ArRoomState(status, placement.scale)
  }

  fun transform(ratio: Float, angle: Float) {
    if (modelShown) placement.transform(ratio, angle)
  }

  private fun showModel(show: Boolean) {
    if (modelShown == show) return
    modelShown = show
    if (show) {
      loaded.attach(scene)
      scene.addEntity(shadow.entity)
    } else {
      loaded.detach(scene)
      scene.removeEntity(shadow.entity)
    }
  }

  private fun acquireImage(frame: Frame) {
    val buffer = try {
      frame.hardwareBuffer
    } catch (_: NotYetAvailableException) {
      return
    }
    try {
      stream.setAcquiredImage(buffer, releases, Runnable(buffer::close))
    } catch (error: Exception) {
      buffer.close()
      throw error
    }
    timestamp = frame.timestamp
    if (!videoShown) scene.addEntity(video.entity)
    videoShown = true
  }

  private fun planePoint(pose: Pose, screen: Offset, y: Float): FloatArray? {
    val x = 2f * screen.x / width - 1f
    val v = 1f - 2f * screen.y / height
    val ray = pose.rotateVector(floatArrayOf((x + projection[8]) / projection[0],
      (v + projection[9]) / projection[5], -1f))
    return horizontalIntersection(pose.translation, ray, y)
  }

  override fun close() {
    anchor?.detach()
    detachSurface()
    loaded.close()
    scene.removeEntity(video.entity)
    scene.removeEntity(shadow.entity)
    video.close()
    shadow.close()
    engine.destroyTexture(videoTexture)
    engine.destroyStream(stream)
    scene.removeEntity(sun)
    engine.destroyEntity(sun)
    entities.destroy(sun)
    scene.indirectLight = null
    ambient.indirectLight?.let(engine::destroyIndirectLight)
    ambient.cubemap?.let(engine::destroyTexture)
    engine.destroyView(view)
    engine.destroyColorGrading(color)
    engine.destroyScene(scene)
    engine.destroyRenderer(renderer)
    engine.destroyCameraComponent(cameraEntity)
    entities.destroy(cameraEntity)
    engine.destroy()
  }

  companion object {
    private const val Near = 0.04
    private const val Far = 100.0

    fun create(context: Context, model: FluidSceneModel): ArRenderSession {
      Utils.init()
      val engine = Engine.create(Engine.Backend.OPENGL)
      var loaded: FluidLoadedScene? = null
      try {
        val scene = model.load(engine)
        loaded = scene
        return ArRenderSession(context, engine, scene)
      } catch (error: Throwable) {
        loaded?.close()
        engine.destroy()
        throw error
      }
    }
  }
}

internal data class ArDrag(val start: Offset, val end: Offset)
