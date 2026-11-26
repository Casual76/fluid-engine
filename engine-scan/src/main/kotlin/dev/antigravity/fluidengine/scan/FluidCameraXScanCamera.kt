/*
 * engine-scan (4.0, F8.17): la fotocamera vera dello scanner, con CameraX (Apache-2.0). Anteprima in una TextureView
 * (il vetro la campiona), analisi a 640 × 480 e scatto alla qualità massima. Codice del Fluid Engine.
 */
package dev.antigravity.fluidengine.scan

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.util.Size
import androidx.camera.core.Camera
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.ImageProxy
import androidx.camera.core.resolutionselector.AspectRatioStrategy
import androidx.camera.core.resolutionselector.ResolutionSelector
import androidx.camera.core.resolutionselector.ResolutionStrategy
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LocalLifecycleOwner
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.suspendCancellableCoroutine
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/**
 * La fotocamera vera dello scanner, accesa finché [FluidScanCamera.Preview] è in composizione. Il permesso
 * `CAMERA` lo dichiara l'app e lo chiede lo scanner, col tasto, nel punto in cui serve (F8.11): senza il permesso
 * [FluidScanCamera.problem] lo dice e lo scanner offre «Consenti la fotocamera».
 *
 * ```kotlin
 * val camera = rememberFluidCameraXScanCamera()
 * FluidDocumentScanner(state, camera, onDone = { result -> … }, onCancel = { … })
 * ```
 */
@Composable
fun rememberFluidCameraXScanCamera(): FluidScanCamera {
  val context = LocalContext.current.applicationContext
  val camera = remember(context) { FluidCameraXScanCamera(context) }
  DisposableEffect(camera) { onDispose { camera.release() } }
  return camera
}

@Stable
internal class FluidCameraXScanCamera(private val context: Context) : FluidScanCamera {
  private val executor: ExecutorService = Executors.newSingleThreadExecutor { r -> Thread(r, "fluid-scan-camera") }
  private var camera: Camera? = null
  private var analyzer: ((FluidScanFrame) -> Unit)? = null
  private var buffer = ByteArray(0)

  override var aspect by mutableFloatStateOf(3f / 4f)
    private set
  override var ready by mutableStateOf(false)
    private set
  override var problem by mutableStateOf<FluidScanCameraProblem?>(null)
    private set
  override var hasTorch by mutableStateOf(false)
    private set
  private var torchOn by mutableStateOf(false)
  override var torch: Boolean
    get() = torchOn
    set(value) {
      torchOn = value
      camera?.cameraControl?.enableTorch(value)
    }

  private var permission by mutableStateOf(granted())
  private fun granted() = ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED

  override fun permissionChanged() {
    permission = granted()
  }

  // 4:3 per tutte e tre: anteprima, analisi e scatto vedono la stessa scena.
  private val ratio = AspectRatioStrategy(androidx.camera.core.AspectRatio.RATIO_4_3, AspectRatioStrategy.FALLBACK_RULE_AUTO)
  private val analysis = ImageAnalysis.Builder()
    .setResolutionSelector(ResolutionSelector.Builder().setAspectRatioStrategy(ratio)
      .setResolutionStrategy(ResolutionStrategy(Size(640, 480), ResolutionStrategy.FALLBACK_RULE_CLOSEST_HIGHER_THEN_LOWER)).build())
    .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
    .setOutputImageFormat(ImageAnalysis.OUTPUT_IMAGE_FORMAT_YUV_420_888)
    .build()
  // Lo scatto: al più 8 MP (3264 × 2448). Una pagina raddrizzata ha 2400 pixel sul lato lungo: di più è memoria sprecata.
  private val still = ImageCapture.Builder()
    .setCaptureMode(ImageCapture.CAPTURE_MODE_MAXIMIZE_QUALITY)
    .setResolutionSelector(ResolutionSelector.Builder().setAspectRatioStrategy(ratio)
      .setResolutionStrategy(ResolutionStrategy(Size(3264, 2448), ResolutionStrategy.FALLBACK_RULE_CLOSEST_LOWER_THEN_HIGHER)).build())
    .build()

  init {
    analysis.setAnalyzer(executor) { image -> image.use(::deliver) }
  }

  private fun deliver(image: ImageProxy) {
    val target = analyzer ?: return
    val plane = image.planes[0]
    val stride = plane.rowStride
    val needed = stride * (image.height - 1) + image.width
    if (buffer.size < needed) buffer = ByteArray(needed)
    val data = plane.buffer
    data.rewind()
    data.get(buffer, 0, minOf(needed, data.remaining()))
    val frame = FluidScanFrame(image.width, image.height, buffer, stride, image.imageInfo.rotationDegrees, image.imageInfo.timestamp / 1_000_000)
    val upright = frame.uprightAspect
    if (upright != aspect) ContextCompat.getMainExecutor(context).execute { aspect = upright }
    target(frame)
  }

  @Composable
  override fun Preview(modifier: Modifier) {
    val lifecycle = LocalLifecycleOwner.current
    val preview = remember { androidx.camera.core.Preview.Builder().setResolutionSelector(ResolutionSelector.Builder().setAspectRatioStrategy(ratio).build()).build() }
    AndroidView(
      factory = { ctx ->
        PreviewView(ctx).apply {
          // Una TextureView: il vetro dei comandi la campiona (una SurfaceView resterebbe nera sotto il vetro).
          implementationMode = PreviewView.ImplementationMode.COMPATIBLE
          scaleType = PreviewView.ScaleType.FILL_CENTER
          preview.setSurfaceProvider(surfaceProvider)
        }
      },
      modifier = modifier,
    )
    val allowed = permission
    LaunchedEffect(lifecycle, allowed) {
      if (!allowed) {
        ready = false
        problem = FluidScanCameraProblem.Permission
        return@LaunchedEffect
      }
      val provider = try {
        val future = ProcessCameraProvider.getInstance(context)
        suspendCancellableCoroutine<ProcessCameraProvider> { c ->
          future.addListener({ c.resumeWith(runCatching { future.get() }) }, ContextCompat.getMainExecutor(context))
        }
      } catch (e: Exception) {
        problem = FluidScanCameraProblem.Unavailable
        return@LaunchedEffect
      }
      try {
        provider.unbindAll()
        val bound = provider.bindToLifecycle(lifecycle, CameraSelector.DEFAULT_BACK_CAMERA, preview, analysis, still)
        camera = bound
        hasTorch = bound.cameraInfo.hasFlashUnit()
        if (torchOn) bound.cameraControl.enableTorch(true)
        problem = null
        ready = true
        awaitCancellation()
      } catch (e: IllegalArgumentException) {
        // Nessuna fotocamera posteriore (un tablet senza, un emulatore).
        problem = FluidScanCameraProblem.Unavailable
      } finally {
        ready = false
        camera = null
        provider.unbindAll()
      }
    }
  }

  override suspend fun analyze(analyzer: (FluidScanFrame) -> Unit) {
    this.analyzer = analyzer
    try {
      awaitCancellation()
    } finally {
      if (this.analyzer === analyzer) this.analyzer = null
    }
  }

  override suspend fun capture(): Bitmap = suspendCancellableCoroutine { continuation ->
    still.takePicture(executor, object : ImageCapture.OnImageCapturedCallback() {
      override fun onCaptureSuccess(image: ImageProxy) {
        val result = runCatching { image.use(::decode) }
        result.fold(continuation::resume, continuation::resumeWithException)
      }

      override fun onError(exception: ImageCaptureException) {
        continuation.resumeWithException(exception)
      }
    })
  }

  /** Il JPEG dello scatto, dritto. */
  private fun decode(image: ImageProxy): Bitmap {
    val bitmap = if (image.format == android.graphics.ImageFormat.JPEG) {
      val data = image.planes[0].buffer
      data.rewind()
      val bytes = ByteArray(data.remaining())
      data.get(bytes)
      BitmapFactory.decodeByteArray(bytes, 0, bytes.size) ?: error("Scatto illeggibile")
    } else {
      image.toBitmap()
    }
    val degrees = image.imageInfo.rotationDegrees
    if (degrees == 0) return bitmap
    val rotated = Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, Matrix().apply { postRotate(degrees.toFloat()) }, true)
    if (rotated !== bitmap) bitmap.recycle()
    return rotated
  }

  fun release() {
    analysis.clearAnalyzer()
    executor.shutdown()
  }
}
