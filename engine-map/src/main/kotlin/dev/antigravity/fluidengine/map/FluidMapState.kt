/*
 * engine-map (4.0, F8.26): lo stato della mappa (la camera, la posizione, lo stile, i comandi). Codice del Fluid Engine.
 */
package dev.antigravity.fluidengine.map

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import kotlin.math.max
import kotlin.math.pow

/** La posizione di chi usa la mappa, come la da' l'app: il punto, l'incertezza in metri e la direzione (se si sa). */
@Immutable
data class FluidMapUserLocation(
  val point: FluidGeoPoint,
  val accuracyMeters: Float? = null,
  val headingDegrees: Float? = null,
)

/**
 * I numeri del movimento della mappa. Apple non pubblica quelli di Mappe: sono **a occhio** (26/11/2026), guardando
 * Mappe di iOS 26 sul tasto della posizione e sul tocco di un grappolo, e vicini a quelli che usa MyCloud Gallery 2 con
 * MapLibre (450 ms).
 */
object FluidMapMotion {
  /** Il volo della camera verso un punto (la posizione, un luogo scelto, un grappolo). A occhio, 26/11/2026. */
  const val FlyMillis: Int = 450

  /** Il ritorno al nord dalla bussola. A occhio, 26/11/2026. */
  const val NorthMillis: Int = 350

  /** Il doppio tocco che avvicina di un passo. A occhio, 26/11/2026. */
  const val DoubleTapMillis: Int = 300

  /** I segnaposto che escono da un grappolo che si divide (e ci rientrano). A occhio, 26/11/2026. */
  const val ClusterMillis: Int = 280

  /** Lo zoom a cui porta il tasto della posizione, se la mappa e' piu' lontana: la scala di un quartiere. */
  const val UserZoom: Double = 15.0
}

/** Se la mappa segue la posizione: [Off] (di serie, e appena il dito la sposta) o [Follow] (il tasto acceso). */
enum class FluidMapTracking { Off, Follow }

/**
 * Lo stato di una [FluidMap]: dove guarda ([camera]), la posizione ([userLocation], data dall'app), se la segue
 * ([tracking]), lo stile scelto ([styleId]) e i comandi della camera ([animateTo], [moveTo], [zoomBy], [resetNorth],
 * [show], [centerOnUser]).
 *
 * **La camera cambia a ogni fotogramma** mentre il dito muove la mappa: si legge nel disegno e nella misura (le
 * posizioni dei segnaposto, la bussola), mai nella composizione, che altrimenti si rifarebbe a ogni fotogramma. Le
 * decisioni che servono alla composizione (il livello dei grappoli, la bussola che compare) passano da un
 * `derivedStateOf` che cambia di rado.
 *
 * Si scrive dal thread principale (la regola degli stati di Compose): il motore dice la camera dai suoi richiami, che lì
 * arrivano.
 */
@Stable
class FluidMapState(camera: FluidMapCamera, styleId: String? = null) {
  /** Dove guarda la mappa, adesso. */
  var camera: FluidMapCamera by mutableStateOf(camera)
    private set

  /** La posizione di chi guarda, se l'app la conosce (e ha il permesso). Null: il tasto della posizione la chiede. */
  var userLocation: FluidMapUserLocation? by mutableStateOf(null)

  /** Se la mappa segue la posizione. Il dito che la sposta la spegne, come in Mappe. */
  var tracking: FluidMapTracking by mutableStateOf(FluidMapTracking.Off)

  /** L'id dello stile scelto nel pannello dei livelli; null il primo. */
  var styleId: String? by mutableStateOf(styleId)

  /** Vero mentre il dito muove la mappa (anche nel lancio che parte dal dito). */
  var isGestureActive: Boolean by mutableStateOf(false)
    private set

  /** Lo zoom piu' lontano e il piu' vicino che il dito e i comandi raggiungono. */
  var minZoom: Double = 1.0
  var maxZoom: Double = 19.0

  /**
   * Quanto del fondo della mappa copre il foglio sopra (il suo detent di riposo): l'attribuzione sta sopra, e i comandi
   * centrano i punti nella parte che si vede. Lo scrive [FluidMapSheet]; un'app con un suo pannello lo scrive lei.
   */
  var bottomObscured: Dp by mutableStateOf(0.dp)

  /** La misura della superficie in pixel e la densita', scritte dalla mappa quando cambiano. */
  var sizePx: IntSize by mutableStateOf(IntSize.Zero)
    internal set
  internal var density: Float = 1f

  /** Col movimento ridotto i voli diventano salti. Lo scrive la mappa dalla politica del movimento. */
  internal var reducedMotion: Boolean = false

  private var driver: FluidMapCameraDriver? = null
  private var tween: Animatable<Float, *>? = null

  /** Il motore collega chi muove la camera (quando la superficie e' pronta). */
  fun attach(driver: FluidMapCameraDriver) {
    this.driver = driver
  }

  /** Il motore stacca [driver] (quando la superficie se ne va). */
  fun detach(driver: FluidMapCameraDriver) {
    if (this.driver === driver) this.driver = null
  }

  /**
   * Il motore dice dove guarda la mappa. [byGesture] vero se l'ha mossa il dito: allora la mappa smette di seguire la
   * posizione.
   */
  fun report(camera: FluidMapCamera, byGesture: Boolean) {
    this.camera = camera
    if (byGesture && tracking == FluidMapTracking.Follow) tracking = FluidMapTracking.Off
  }

  /** Il motore dice quando il dito comincia e finisce di muovere la mappa. */
  fun reportGesture(active: Boolean) {
    isGestureActive = active
    if (active && tracking == FluidMapTracking.Follow) tracking = FluidMapTracking.Off
  }

  /** Chi ascolta la mappa vera che ridisegna (la mappa, perche' il vetro adattivo rilegga lo sfondo). */
  internal var onRendered: (() -> Unit)? = null

  /** Chi ascolta il tocco sulla mappa (la mappa, per togliere la scelta). */
  internal var onTap: ((FluidGeoPoint) -> Unit)? = null

  /** Il motore dice che il dito ha toccato la mappa in [point], senza muoverla. */
  fun reportTap(point: FluidGeoPoint) {
    onTap?.invoke(point)
  }

  /** La proiezione della camera di adesso sulla superficie; null prima della prima misura. */
  fun projector(): FluidMapProjector? {
    val size = sizePx
    if (size.width <= 0 || size.height <= 0) return null
    return FluidMapProjector(camera, size.width.toFloat(), size.height.toFloat(), density)
  }

  /** Porta la camera a [target] subito. */
  fun moveTo(target: FluidMapCamera) {
    val clamped = clamp(target)
    val d = driver
    if (d != null) d.jump(clamped) else camera = clamped
  }

  /**
   * Porta la camera a [target] con un volo di [durationMillis] (col movimento ridotto, un salto). Si ferma se il dito
   * riprende la mappa.
   */
  suspend fun animateTo(target: FluidMapCamera, durationMillis: Int = FluidMapMotion.FlyMillis) {
    val clamped = clamp(target)
    val d = driver
    if (reducedMotion || durationMillis <= 0) {
      moveTo(clamped)
      return
    }
    if (d != null) {
      d.fly(clamped, durationMillis)
      return
    }
    fly(clamped, durationMillis)
  }

  /** Avvicina (o allontana, con [steps] negativo) di [steps] passi attorno al centro. */
  suspend fun zoomBy(steps: Double) {
    animateTo(camera.copy(zoom = camera.zoom + steps), FluidMapMotion.DoubleTapMillis)
  }

  /** Riporta il nord in alto (il tocco sulla bussola). */
  suspend fun resetNorth() {
    val target = camera.copy(bearing = nearestNorth(camera.bearing))
    animateTo(target, FluidMapMotion.NorthMillis)
  }

  /** Mostra tutti i [points] (con [paddingDp] attorno, sopra il foglio), nord in alto. Niente se l'elenco e' vuoto. */
  suspend fun show(points: Collection<FluidGeoPoint>, paddingDp: Double = 56.0, maxZoom: Double = 16.0) {
    val bounds = FluidGeoBounds.of(points) ?: return
    val size = sizePx
    if (size.width <= 0 || size.height <= 0) return
    val w = size.width / density.toDouble()
    val visibleH = size.height / density.toDouble() - bottomObscured.value
    val fit = FluidMapProjection.fit(bounds, w, max(visibleH, 1.0), paddingDp, maxZoom)
    animateTo(shiftedForSheet(fit))
  }

  /**
   * Porta la mappa sulla posizione e la fa seguire ([FluidMapTracking.Follow]); vero se la posizione c'e'. Se la mappa e'
   * piu' lontana di [FluidMapMotion.UserZoom] si avvicina fin li', se e' piu' vicina resta allo zoom che ha.
   */
  suspend fun centerOnUser(): Boolean {
    val here = userLocation ?: return false
    tracking = FluidMapTracking.Follow
    val zoom = max(camera.zoom, FluidMapMotion.UserZoom)
    animateTo(shiftedForSheet(camera.copy(center = here.point, zoom = zoom)))
    return true
  }

  /**
   * La camera che mette il centro di [target] al centro della parte che si vede (sopra il foglio): il centro vero scende
   * di meta' del foglio.
   */
  internal fun shiftedForSheet(target: FluidMapCamera): FluidMapCamera {
    val obscured = bottomObscured.value.toDouble()
    if (obscured <= 0.0 || sizePx.height <= 0) return target
    val size = sizePx
    val projector = FluidMapProjector(target, size.width.toFloat(), size.height.toFloat(), density)
    val shiftPx = (obscured / 2.0 * density).toFloat()
    return projector.panned(androidx.compose.ui.geometry.Offset(0f, -shiftPx))
  }

  private fun clamp(target: FluidMapCamera): FluidMapCamera {
    val zoom = target.zoom.coerceIn(minZoom, maxZoom)
    val lat = target.center.latitude.coerceIn(-FluidMapProjection.MaxLatitude, FluidMapProjection.MaxLatitude)
    return FluidMapCamera(FluidGeoPoint(lat, target.center.wrappedLongitude), zoom, target.bearing)
  }

  /**
   * Il volo dello stato stesso, per un motore che non vola da se' (il disegno): il centro va per la strada piu' breve sul
   * mondo, lo zoom in scala logaritmica (lo stesso tempo per ogni raddoppio), la direzione per l'angolo piu' breve.
   */
  private suspend fun fly(target: FluidMapCamera, durationMillis: Int) {
    val from = camera
    val fx = FluidMapProjection.x(from.center.longitude)
    val fy = FluidMapProjection.y(from.center.latitude)
    var tx = FluidMapProjection.x(target.center.longitude)
    val ty = FluidMapProjection.y(target.center.latitude)
    if (tx - fx > 0.5) tx -= 1.0 else if (tx - fx < -0.5) tx += 1.0
    var turn = (target.bearing - from.bearing) % 360.0
    if (turn > 180) turn -= 360 else if (turn < -180) turn += 360
    tween?.stop()
    val progress = Animatable(0f)
    tween = progress
    try {
      progress.animateTo(1f, tween(durationMillis, easing = FastOutSlowInEasing)) {
        val t = value.toDouble()
        // Lo zoom e' lineare, quindi la scala e' esponenziale: il centro segue la scala, cosi' il punto d'arrivo non
        // scivola via mentre ci si avvicina.
        val zoom = from.zoom + (target.zoom - from.zoom) * t
        val k = scaleProgress(from.zoom, target.zoom, t)
        val x = fx + (tx - fx) * k
        val y = fy + (ty - fy) * k
        camera = FluidMapCamera(
          FluidGeoPoint(FluidMapProjection.latitude(y), FluidMapProjection.longitude(x)), zoom, from.bearing + turn * t,
        )
      }
    } finally {
      if (tween === progress) tween = null
    }
  }

  /** Ferma il volo dello stato (il dito riprende la mappa). */
  internal suspend fun stopFlight() {
    tween?.stop()
  }

  companion object {
    /** Salva camera e stile (la posizione no: la ridice l'app). */
    val Saver: Saver<FluidMapState, Any> = Saver(
      save = { s ->
        listOf(s.camera.center.latitude, s.camera.center.longitude, s.camera.zoom, s.camera.bearing, s.styleId ?: "")
      },
      restore = { v ->
        @Suppress("UNCHECKED_CAST") val l = v as List<Any>
        FluidMapState(
          FluidMapCamera(FluidGeoPoint(l[0] as Double, l[1] as Double), l[2] as Double, l[3] as Double),
          (l[4] as String).ifEmpty { null },
        )
      },
    )
  }
}

/** Lo stato di una mappa, ricordato anche dopo la ricreazione dell'attivita'. */
@Composable
fun rememberFluidMapState(initial: FluidMapCamera, styleId: String? = null): FluidMapState =
  rememberSaveable(saver = FluidMapState.Saver) { FluidMapState(initial, styleId) }

/** La direzione «nord» piu' vicina a [bearing] (0, 360, -360…): la bussola gira per la strada breve. */
internal fun nearestNorth(bearing: Double): Double = Math.round(bearing / 360.0) * 360.0

/**
 * Quanto del tragitto del centro e' fatto al tempo [t], se lo zoom va da [from] a [to] in modo lineare: la frazione della
 * scala fatta, cosi' che il punto d'arrivo resti fermo sullo schermo mentre ci si avvicina (il volo «di van Wijk», nella
 * forma breve).
 */
internal fun scaleProgress(from: Double, to: Double, t: Double): Double {
  val dz = to - from
  if (kotlin.math.abs(dz) < 1e-6) return t
  // La scala al tempo t, rispetto a quella di partenza: 2^(dz·t). Il centro avanza come (1 - 2^(-dz·t)) / (1 - 2^(-dz)).
  val num = 1.0 - 2.0.pow(-dz * t)
  val den = 1.0 - 2.0.pow(-dz)
  return if (kotlin.math.abs(den) < 1e-9) t else num / den
}
