/*
 * engine-map (4.0, F8.26): i punti, i riquadri, la camera e la proiezione della mappa. Codice puro, senza Android: si
 * prova sulla JVM. Codice del Fluid Engine.
 */
package dev.antigravity.fluidengine.map

import androidx.compose.runtime.Immutable
import androidx.compose.ui.geometry.Offset
import java.util.Locale
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.atan
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.floor
import kotlin.math.ln
import kotlin.math.log2
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.tan

/**
 * Un punto sulla Terra, in gradi WGS84 (quelli del GPS, di OpenStreetMap e di `CLLocationCoordinate2D`).
 *
 * La longitudine si riporta fra -180 e 180; la latitudine resta quella data (fra -90 e 90): la mappa la ferma a
 * ±85,05° ([FluidMapProjection.MaxLatitude]), dove finisce il quadrato di Mercatore.
 */
@Immutable
data class FluidGeoPoint(val latitude: Double, val longitude: Double) {
  init {
    require(latitude.isFinite() && latitude in -90.0..90.0) { "Latitudine fuori dalla Terra: $latitude" }
    require(longitude.isFinite()) { "Longitudine non valida: $longitude" }
  }

  /** La longitudine riportata fra -180 (incluso) e 180 (escluso). */
  val wrappedLongitude: Double get() = wrapLongitude(longitude)

  internal companion object {
    fun wrapLongitude(longitude: Double): Double {
      val wrapped = ((longitude + 180.0) % 360.0 + 360.0) % 360.0 - 180.0
      return if (wrapped == -180.0 && longitude > 0) 180.0 - 1e-12 else wrapped
    }
  }
}

/**
 * Un riquadro sulla Terra, da [south] a [north] e da [west] a [east] (in gradi). Non attraversa l'antimeridiano: un
 * riquadro che lo attraversa si da' come due.
 */
@Immutable
data class FluidGeoBounds(val south: Double, val west: Double, val north: Double, val east: Double) {
  init {
    require(south <= north) { "Il sud ($south) sta sopra il nord ($north)" }
    require(west <= east) { "L'ovest ($west) sta a destra dell'est ($east)" }
  }

  val center: FluidGeoPoint get() = FluidGeoPoint((south + north) / 2, (west + east) / 2)

  operator fun contains(point: FluidGeoPoint): Boolean =
    point.latitude in south..north && point.wrappedLongitude in west..east

  /** Lo stesso riquadro allargato fino a contenere [point]. */
  fun including(point: FluidGeoPoint): FluidGeoBounds = FluidGeoBounds(
    min(south, point.latitude), min(west, point.wrappedLongitude),
    max(north, point.latitude), max(east, point.wrappedLongitude),
  )

  companion object {
    /** Il riquadro piu' piccolo che contiene tutti i [points]; null se non ce n'e' nessuno. */
    fun of(points: Iterable<FluidGeoPoint>): FluidGeoBounds? {
      var bounds: FluidGeoBounds? = null
      for (point in points) {
        bounds = bounds?.including(point)
          ?: FluidGeoBounds(point.latitude, point.wrappedLongitude, point.latitude, point.wrappedLongitude)
      }
      return bounds
    }
  }
}

/**
 * Dove guarda la mappa: il [center], lo [zoom] (0 la Terra intera in 512 dp, ogni passo il doppio, come MapLibre e
 * `MKMapCamera` con la sua distanza) e la [bearing], i gradi in senso orario da nord del lato alto dello schermo (0: nord
 * in alto; `MapCamera.heading` di MapKit).
 */
@Immutable
data class FluidMapCamera(val center: FluidGeoPoint, val zoom: Double, val bearing: Double = 0.0) {
  init {
    require(zoom.isFinite()) { "Zoom non valido: $zoom" }
    require(bearing.isFinite()) { "Direzione non valida: $bearing" }
  }

  /** La direzione riportata fra 0 (incluso) e 360 (escluso). */
  val normalizedBearing: Double get() = ((bearing % 360.0) + 360.0) % 360.0

  /** Vero se il nord non e' in alto (oltre mezzo grado: sotto, la bussola non si mostra, come in Mappe). */
  val isRotated: Boolean get() = FluidMapProjection.bearingDistance(bearing, 0.0) > FluidMapProjection.NorthTolerance
}

/**
 * La proiezione di Mercatore della mappa (EPSG:3857), la stessa di MapLibre, MapKit e OpenStreetMap: a zoom `z` la Terra
 * e' un quadrato di `512 · 2^z` dp ([TileSize]: le tessere vettoriali di MapLibre sono da 512). Le coordinate del
 * «mondo» qui sono **normalizzate**: da 0 a 1 in orizzontale (da -180° a 180°) e in verticale (da 85,05° nord a 85,05°
 * sud); i dp sono `normalizzato · 512 · 2^z`.
 */
object FluidMapProjection {
  /** Il lato di una tessera a zoom intero, in dp: 512, come MapLibre (`util::tileSize_D`). Fiducia alta. */
  const val TileSize: Double = 512.0

  /** La latitudine dove finisce il quadrato di Mercatore: `atan(sinh(π))`, in gradi. */
  const val MaxLatitude: Double = 85.05112877980659

  /** Sotto questa differenza (in gradi) la mappa e' «a nord»: la bussola non c'e'. A occhio, 26/11/2026. */
  const val NorthTolerance: Double = 0.5

  /** Il lato del mondo a [zoom], in dp. */
  fun worldSize(zoom: Double): Double = TileSize * 2.0.pow(zoom)

  /** La x normalizzata (0..1) di una longitudine. */
  fun x(longitude: Double): Double = (FluidGeoPoint.wrapLongitude(longitude) + 180.0) / 360.0

  /** La y normalizzata (0..1, 0 in cima) di una latitudine, ferma ai bordi del quadrato. */
  fun y(latitude: Double): Double {
    val phi = latitude.coerceIn(-MaxLatitude, MaxLatitude) * PI / 180.0
    return (1.0 - ln(tan(phi) + 1.0 / cos(phi)) / PI) / 2.0
  }

  /** La longitudine di una x normalizzata (anche fuori da 0..1: si riporta). */
  fun longitude(x: Double): Double = FluidGeoPoint.wrapLongitude(x * 360.0 - 180.0)

  /** La latitudine di una y normalizzata (ferma ai bordi del quadrato). */
  fun latitude(y: Double): Double {
    val n = PI * (1.0 - 2.0 * y.coerceIn(0.0, 1.0))
    return atan(0.5 * (exp(n) - exp(-n))) * 180.0 / PI
  }

  /** La distanza piu' breve fra due direzioni, in gradi (0..180). */
  fun bearingDistance(a: Double, b: Double): Double {
    val d = abs(((a - b) % 360.0 + 540.0) % 360.0 - 180.0)
    return d
  }

  /**
   * Lo zoom e il centro che mostrano tutto [bounds] in una finestra di [widthDp] × [heightDp], lasciando [paddingDp] su
   * ogni lato, senza superare [maxZoom] (un punto solo non diventa una tessera a zoom 22). Nord in alto.
   */
  fun fit(bounds: FluidGeoBounds, widthDp: Double, heightDp: Double, paddingDp: Double = 48.0, maxZoom: Double = 16.0): FluidMapCamera {
    val spanX = abs(x(bounds.east) - x(bounds.west)).coerceAtLeast(1e-12)
    val spanY = abs(y(bounds.south) - y(bounds.north)).coerceAtLeast(1e-12)
    val usableW = (widthDp - 2 * paddingDp).coerceAtLeast(1.0)
    val usableH = (heightDp - 2 * paddingDp).coerceAtLeast(1.0)
    val zoom = min(log2(usableW / (spanX * TileSize)), log2(usableH / (spanY * TileSize)))
    val cx = (x(bounds.west) + x(bounds.east)) / 2
    val cy = (y(bounds.north) + y(bounds.south)) / 2
    return FluidMapCamera(FluidGeoPoint(latitude(cy), longitude(cx)), zoom.coerceAtMost(maxZoom).coerceAtLeast(0.0))
  }
}

/**
 * La proiezione di una [camera] su una finestra di [widthPx] × [heightPx] pixel con [density] pixel per dp: dal punto
 * allo schermo e ritorno, e i movimenti del dito (spostare, ingrandire attorno a un punto, ruotare). Pura e senza
 * allocazioni oltre al risultato: si usa nel disegno e nella misura a ogni fotogramma.
 */
class FluidMapProjector(val camera: FluidMapCamera, val widthPx: Float, val heightPx: Float, val density: Float) {
  private val world = FluidMapProjection.worldSize(camera.zoom) * density
  private val cx = FluidMapProjection.x(camera.center.longitude)
  private val cy = FluidMapProjection.y(camera.center.latitude)
  private val radians = camera.bearing * PI / 180.0
  private val cosB = cos(radians)
  private val sinB = sin(radians)

  /** Il lato del mondo in pixel. */
  val worldPx: Double get() = world

  /** Dove cade [point] sullo schermo, in pixel dall'angolo in alto a sinistra. */
  fun toScreen(point: FluidGeoPoint): Offset = toScreenNormalized(FluidMapProjection.x(point.longitude), FluidMapProjection.y(point.latitude))

  /** Dove cade il punto normalizzato ([x], [y]) sullo schermo; la x prende il giro di mondo piu' vicino al centro. */
  fun toScreenNormalized(x: Double, y: Double): Offset {
    var dx = x - cx
    if (dx > 0.5) dx -= 1.0 else if (dx < -0.5) dx += 1.0
    val wx = dx * world
    val wy = (y - cy) * world
    val sx = wx * cosB + wy * sinB
    val sy = -wx * sinB + wy * cosB
    return Offset((sx + widthPx / 2.0).toFloat(), (sy + heightPx / 2.0).toFloat())
  }

  /** Il punto della Terra sotto il pixel [screen]. */
  fun toGeo(screen: Offset): FluidGeoPoint {
    val (x, y) = normalizedAt(screen)
    return FluidGeoPoint(FluidMapProjection.latitude(y), FluidMapProjection.longitude(x))
  }

  /** Le coordinate normalizzate sotto il pixel [screen] (la x non riportata fra 0 e 1). */
  fun normalizedAt(screen: Offset): Pair<Double, Double> {
    val sx = screen.x - widthPx / 2.0
    val sy = screen.y - heightPx / 2.0
    // L'inversa della rotazione.
    val wx = sx * cosB - sy * sinB
    val wy = sx * sinB + sy * cosB
    return Pair(cx + wx / world, cy + wy / world)
  }

  /** La camera dopo che il dito ha trascinato la mappa di [deltaPx]: il punto sotto il dito resta sotto il dito. */
  fun panned(deltaPx: Offset): FluidMapCamera {
    val (x, y) = normalizedAt(Offset(widthPx / 2f - deltaPx.x, heightPx / 2f - deltaPx.y))
    return camera.copy(center = FluidGeoPoint(FluidMapProjection.latitude(y), FluidMapProjection.longitude(x)))
  }

  /**
   * La camera dopo uno zoom di [zoomDelta] passi e una rotazione di [rotationDegrees] attorno al pixel [focus] (il punto
   * fra le due dita, o il doppio tocco): il punto sotto [focus] resta lì.
   */
  fun transformed(focus: Offset, zoomDelta: Double, rotationDegrees: Double, minZoom: Double, maxZoom: Double): FluidMapCamera {
    val anchor = normalizedAt(focus)
    val zoom = (camera.zoom + zoomDelta).coerceIn(minZoom, maxZoom)
    val bearing = camera.bearing + rotationDegrees
    // Il centro nuovo: dal punto fisso, all'indietro della distanza dello schermo nella scala e direzione nuove.
    val world2 = FluidMapProjection.worldSize(zoom) * density
    val rad = bearing * PI / 180.0
    val c = cos(rad)
    val s = sin(rad)
    val sx = focus.x - widthPx / 2.0
    val sy = focus.y - heightPx / 2.0
    val wx = sx * c - sy * s
    val wy = sx * s + sy * c
    val x = anchor.first - wx / world2
    val y = (anchor.second - wy / world2).coerceIn(0.0, 1.0)
    return FluidMapCamera(FluidGeoPoint(FluidMapProjection.latitude(y), FluidMapProjection.longitude(x)), zoom, bearing)
  }

  /** Il raggio, in coordinate normalizzate, del cerchio che contiene tutto lo schermo (con qualunque rotazione). */
  fun screenRadiusNormalized(): Double {
    val half = kotlin.math.sqrt((widthPx / 2.0) * (widthPx / 2.0) + (heightPx / 2.0) * (heightPx / 2.0))
    return half / world
  }

  /** La x e la y normalizzate del centro. */
  internal val centerX: Double get() = cx
  internal val centerY: Double get() = cy
}

/**
 * Le coordinate scritte: come le mostra Mappe («43,77310° N, 11,25560° E», cinque decimali: circa un metro) e come si
 * copiano («43.77310, 11.25560», col punto: il formato che ogni app di mappe e ogni campo di ricerca capisce).
 */
object FluidGeoFormat {
  /** Le cifre dopo la virgola: cinque, circa 1,1 m all'equatore. Fiducia alta (la scheda di Mappe di iOS 26). */
  const val Decimals: Int = 5

  /** Il punto da leggere, nella lingua del telefono: «43,77310° N, 11,25560° E». */
  fun display(point: FluidGeoPoint, locale: Locale = Locale.getDefault()): String {
    val lat = String.format(locale, "%.${Decimals}f", abs(point.latitude))
    val lon = String.format(locale, "%.${Decimals}f", abs(point.wrappedLongitude))
    val ns = if (point.latitude >= 0) "N" else "S"
    val ew = if (point.wrappedLongitude >= 0) "E" else "O".takeIf { locale.language == "it" } ?: "W"
    return "$lat° $ns, $lon° $ew"
  }

  /** Il punto da copiare, sempre col punto decimale: «43.77310, 11.25560». */
  fun copy(point: FluidGeoPoint): String =
    String.format(Locale.ROOT, "%.${Decimals}f, %.${Decimals}f", point.latitude, point.wrappedLongitude)

  /** L'indirizzo `geo:` del punto, per «Indicazioni» e per le altre app di mappe (RFC 5870, con l'etichetta). */
  fun geoUri(point: FluidGeoPoint, label: String? = null): String {
    val at = String.format(Locale.ROOT, "%.6f,%.6f", point.latitude, point.wrappedLongitude)
    val q = if (label.isNullOrBlank()) at else "$at(${java.net.URLEncoder.encode(label, "UTF-8").replace("+", "%20")})"
    return "geo:$at?q=$q"
  }

  /** Il collegamento a OpenStreetMap del punto, per «Condividi»: si apre in ogni browser. */
  fun openStreetMapUrl(point: FluidGeoPoint, zoom: Int = 17): String {
    val lat = String.format(Locale.ROOT, "%.${Decimals}f", point.latitude)
    val lon = String.format(Locale.ROOT, "%.${Decimals}f", point.wrappedLongitude)
    return "https://www.openstreetmap.org/?mlat=$lat&mlon=$lon#map=$zoom/$lat/$lon"
  }
}

/** Il livello dei grappoli per uno [zoom]: il passo intero sotto, come Supercluster e MapKit. */
internal fun clusterLevel(zoom: Double, minZoom: Int, maxZoom: Int): Int = floor(zoom).toInt().coerceIn(minZoom, maxZoom)
