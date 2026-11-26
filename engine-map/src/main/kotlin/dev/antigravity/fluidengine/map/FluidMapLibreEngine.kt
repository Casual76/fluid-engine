/*
 * engine-map (4.0, F8.26): la mappa vera, con MapLibre Native (BSD-2-Clause) e le tessere di OpenStreetMap. Codice del
 * Fluid Engine.
 */
package dev.antigravity.fluidengine.map

import android.content.ComponentCallbacks2
import android.content.Context
import android.content.res.Configuration
import android.os.Build
import android.os.Trace
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.NonRestartableComposable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import org.maplibre.android.MapLibre
import org.maplibre.android.camera.CameraPosition
import org.maplibre.android.camera.CameraUpdateFactory
import org.maplibre.android.geometry.LatLng
import org.maplibre.android.maps.MapLibreMap
import org.maplibre.android.maps.MapLibreMapOptions
import org.maplibre.android.maps.MapView
import org.maplibre.android.maps.Style
import org.maplibre.android.snapshotter.MapSnapshotter
import kotlin.coroutines.resume

/**
 * Come si disegna la mappa vera.
 *
 * - [Texture]: una `TextureView`, disegnata dentro Compose. Il vetro dei comandi e del foglio la rifrange e la sfoca,
 *   come in Mappe. Costa una copia della tessera per fotogramma (la misura sul Tab S9 e' nella scheda del modulo).
 * - [Surface]: una `SurfaceView`, in una finestra sua: la strada piu' leggera, ma il vetro non la vede e sopra la mappa
 *   si vede chiaro, senza sfocatura (resta leggibile: la tinta e il filo ci sono).
 */
enum class FluidMapRenderMode { Texture, Surface }

/**
 * La mappa vera, con MapLibre Native e gli stili di [FluidMapStyles] (OpenFreeMap di serie).
 *
 * - **Pigra.** MapLibre parte al primo fotogramma dopo quello in cui la mappa compare, mai all'avvio dell'app: la pagina
 *   si apre col colore dello stile e la mappa arriva subito dopo.
 * - **I gesti sono di MapLibre** (trascinare, lanciare, pizzicare, ruotare, doppio tocco), senza inclinazione: la camera
 *   la dice allo stato a ogni fotogramma. I comandi di MapLibre (bussola, logo, attribuzione) sono spenti: li disegna
 *   [FluidMap], di vetro, con l'attribuzione sempre in vista.
 * - **Il ciclo di vita** della `MapView` segue quello della pagina, e alla memoria che manca la libera.
 * - **La cartolina** e' un `MapSnapshotter`: un'immagine ferma, che poi non costa piu' niente.
 *
 * Il tempo di disegno di ogni fotogramma di MapLibre e' un contatore di traccia, `FluidMap.drawUs` (Perfetto), per le
 * misure sul telefono.
 *
 * MapLibre non si disegna in Robolectric: le prove sul computer usano [FluidSketchMapEngine].
 */
class FluidMapLibreEngine(
  context: Context,
  val renderMode: FluidMapRenderMode = FluidMapRenderMode.Texture,
) : FluidMapEngine {
  private val appContext = context.applicationContext

  override val glassSamplesMap: Boolean get() = renderMode == FluidMapRenderMode.Texture

  @Composable
  override fun Surface(state: FluidMapState, style: FluidMapStyle, dark: Boolean, modifier: Modifier) {
    val context = LocalContext.current
    var ready by remember { mutableStateOf(MapLibre.hasInstance()) }
    LaunchedEffect(Unit) {
      if (!ready) {
        // Un fotogramma prima: la pagina compare col fondo dello stile, poi parte il motore.
        withFrameNanos { }
        ensureStarted(appContext)
        ready = true
      }
    }
    val placeholder = if (dark) style.darkSwatch else style.swatch
    if (!ready) {
      Box(modifier.fillMaxSize().background(placeholder))
      return
    }
    val currentStyle by rememberUpdatedState(style.uriFor(dark))
    val view = remember(context) {
      val options = MapLibreMapOptions.createFromAttributes(context)
        .textureMode(renderMode == FluidMapRenderMode.Texture)
        .camera(state.camera.toPosition())
        .attributionEnabled(false)
        .logoEnabled(false)
        .compassEnabled(false)
        .foregroundLoadColor(placeholder.toArgbInt())
      MapView(context, options)
    }
    MapViewLifecycle(view)
    val driver = remember(view) { MapLibreDriver(state) }
    DisposableEffect(view, state) {
      val frames = MapView.OnDidFinishRenderingFrameListener { _, _, renderSeconds ->
        if (Build.VERSION.SDK_INT >= 29) Trace.setCounter("FluidMap.drawUs", (renderSeconds * 1_000_000).toLong())
        state.onRendered?.invoke()
      }
      view.addOnDidFinishRenderingFrameListener(frames)
      view.getMapAsync { map ->
        map.uiSettings.apply {
          isTiltGesturesEnabled = false
          isCompassEnabled = false
          isLogoEnabled = false
          isAttributionEnabled = false
        }
        map.setMinZoomPreference(state.minZoom)
        map.setMaxZoomPreference(state.maxZoom)
        driver.bind(map)
        state.attach(driver)
        map.setStyle(Style.Builder().fromUri(currentStyle))
      }
      onDispose {
        view.removeOnDidFinishRenderingFrameListener(frames)
        state.detach(driver)
        driver.unbind()
      }
    }
    LaunchedEffect(driver, style.id, dark) {
      driver.map?.let { map ->
        map.setMaxZoomPreference(minOf(state.maxZoom, style.maxZoom))
        map.setStyle(Style.Builder().fromUri(style.uriFor(dark)))
      }
    }
    AndroidView(factory = { view }, modifier = modifier.fillMaxSize())
  }

  override suspend fun snapshot(request: FluidMapSnapshotRequest): ImageBitmap? = withContext(Dispatchers.Main.immediate) {
    ensureStarted(appContext)
    val widthDp = (request.widthPx / request.density).toInt().coerceAtLeast(1)
    val heightDp = (request.heightPx / request.density).toInt().coerceAtLeast(1)
    suspendCancellableCoroutine { continuation ->
      // Le misure in dp con la densita' dello schermo: lo zoom vuol dire la stessa cosa della mappa viva.
      val options = MapSnapshotter.Options(widthDp, heightDp)
        .withStyleBuilder(Style.Builder().fromUri(request.style.uriFor(request.dark)))
        .withCameraPosition(request.camera.toPosition())
        .withPixelRatio(request.density)
        .withLogo(false)
        .withAttribution(false)
      val snapshotter = MapSnapshotter(appContext, options)
      continuation.invokeOnCancellation { snapshotter.cancel() }
      snapshotter.start(
        { snapshot -> if (continuation.isActive) continuation.resume(snapshot.bitmap.asImageBitmap()) },
        { if (continuation.isActive) continuation.resume(null) },
      )
    }
  }

  companion object {
    /** Avvia MapLibre una volta per processo (dal thread principale). Niente chiave: OpenFreeMap non ne chiede. */
    fun ensureStarted(context: Context) {
      if (!MapLibre.hasInstance()) MapLibre.getInstance(context.applicationContext)
    }
  }
}

/** Chi muove la camera di MapLibre per lo stato, e gli dice dove guarda. Tutto sul thread principale. */
private class MapLibreDriver(private val state: FluidMapState) : FluidMapCameraDriver {
  var map: MapLibreMap? = null
    private set
  private var byGesture = false

  fun bind(map: MapLibreMap) {
    this.map = map
    map.addOnCameraMoveStartedListener { reason ->
      byGesture = reason == MapLibreMap.OnCameraMoveStartedListener.REASON_API_GESTURE
      if (byGesture) state.reportGesture(true)
    }
    map.addOnCameraMoveListener { state.report(map.cameraPosition.toFluid(state.camera), byGesture) }
    map.addOnCameraIdleListener {
      state.report(map.cameraPosition.toFluid(state.camera), byGesture)
      if (byGesture) state.reportGesture(false)
      byGesture = false
    }
    map.addOnMapClickListener { latLng ->
      state.reportTap(FluidGeoPoint(latLng.latitude.coerceIn(-90.0, 90.0), latLng.longitude))
      false
    }
    state.report(map.cameraPosition.toFluid(state.camera), byGesture = false)
  }

  fun unbind() {
    map = null
  }

  override fun jump(camera: FluidMapCamera) {
    val m = map ?: return
    m.cancelTransitions()
    m.moveCamera(CameraUpdateFactory.newCameraPosition(camera.toPosition()))
  }

  override suspend fun fly(camera: FluidMapCamera, durationMillis: Int) {
    val m = map ?: return
    suspendCancellableCoroutine { continuation ->
      continuation.invokeOnCancellation { m.cancelTransitions() }
      m.easeCamera(
        CameraUpdateFactory.newCameraPosition(camera.toPosition()), durationMillis, true,
        object : MapLibreMap.CancelableCallback {
          override fun onCancel() { if (continuation.isActive) continuation.resume(Unit) }
          override fun onFinish() { if (continuation.isActive) continuation.resume(Unit) }
        },
      )
    }
  }
}

private fun FluidMapCamera.toPosition(): CameraPosition = CameraPosition.Builder()
  .target(LatLng(center.latitude, center.wrappedLongitude))
  .zoom(zoom)
  .bearing(normalizedBearing)
  .tilt(0.0)
  .build()

private fun CameraPosition.toFluid(fallback: FluidMapCamera): FluidMapCamera {
  val t = target ?: return fallback
  return FluidMapCamera(FluidGeoPoint(t.latitude.coerceIn(-90.0, 90.0), t.longitude), zoom, bearing)
}

private fun androidx.compose.ui.graphics.Color.toArgbInt(): Int {
  val a = (alpha * 255).toInt()
  val r = (red * 255).toInt()
  val g = (green * 255).toInt()
  val b = (blue * 255).toInt()
  return (a shl 24) or (r shl 16) or (g shl 8) or b
}

/**
 * Il ciclo di vita della `MapView` segue quello della pagina (MapLibre lo vuole): creata, avviata, ripresa e al
 * contrario; con l'app nascosta o la memoria che manca, la libera. Come MyCloud Gallery 2 (`MapViewLifecycle`).
 */
@Composable
@NonRestartableComposable
private fun MapViewLifecycle(view: MapView) {
  val lifecycle = LocalLifecycleOwner.current.lifecycle
  val context = LocalContext.current
  DisposableEffect(lifecycle, view) {
    var created = false
    var started = false
    var resumed = false
    val observer = LifecycleEventObserver { _, event ->
      when (event) {
        Lifecycle.Event.ON_CREATE -> if (!created) { view.onCreate(null); created = true }
        Lifecycle.Event.ON_START -> if (created && !started) { view.onStart(); started = true }
        Lifecycle.Event.ON_RESUME -> if (started && !resumed) { view.onResume(); resumed = true }
        Lifecycle.Event.ON_PAUSE -> if (resumed) { view.onPause(); resumed = false }
        Lifecycle.Event.ON_STOP -> if (started) { view.onStop(); started = false }
        Lifecycle.Event.ON_DESTROY -> if (created) { view.onDestroy(); created = false }
        else -> Unit
      }
    }
    lifecycle.addObserver(observer)
    onDispose {
      lifecycle.removeObserver(observer)
      if (resumed) view.onPause()
      if (started) view.onStop()
      if (created) view.onDestroy()
    }
  }
  DisposableEffect(context, view) {
    val callbacks = object : ComponentCallbacks2 {
      override fun onTrimMemory(level: Int) {
        if (level >= ComponentCallbacks2.TRIM_MEMORY_UI_HIDDEN) view.onLowMemory()
      }

      override fun onConfigurationChanged(newConfig: Configuration) = Unit

      @Suppress("OVERRIDE_DEPRECATION")
      override fun onLowMemory() = view.onLowMemory()
    }
    context.registerComponentCallbacks(callbacks)
    onDispose { context.unregisterComponentCallbacks(callbacks) }
  }
}
