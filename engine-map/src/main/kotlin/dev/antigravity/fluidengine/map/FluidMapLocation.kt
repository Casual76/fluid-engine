/*
 * engine-map (4.0, F8.26): la posizione vera, senza Play Services, e le licenze del modulo. Codice del Fluid Engine.
 */
package dev.antigravity.fluidengine.map

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.Build
import android.os.Looper
import android.os.SystemClock
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import dev.antigravity.fluidengine.ui.fluid.FluidLicense
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow

/**
 * La posizione del telefono con `LocationManager`, senza Play Services: il fornitore «fused» di Android 12 quando c'e',
 * altrimenti la rete e il GPS (il migliore acceso). Senza Play Services la posizione e' meno precisa al chiuso: l'alone
 * dell'incertezza lo dice.
 *
 * **I permessi li chiede l'app** (`ACCESS_COARSE_LOCATION`, e `ACCESS_FINE_LOCATION` se serve il punto esatto: le righe
 * del manifest sono nella scheda del modulo), nel punto giusto (`FluidPermissionButton` di engine-ui, sul tasto della
 * posizione: [FluidMap] chiama `onLocationRequest`). Senza permesso [updates] non da' niente e non va in errore.
 */
class FluidSystemLocation(context: Context) {
  private val appContext = context.applicationContext
  private val manager: LocationManager? = appContext.getSystemService(LocationManager::class.java)

  /** Vero se l'app ha almeno la posizione approssimata. */
  fun hasPermission(): Boolean =
    appContext.checkSelfPermission(Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED ||
      appContext.checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED

  /** Il fornitore da ascoltare: fused, poi GPS (col permesso preciso), poi la rete. Null se non c'e' niente di acceso. */
  internal fun provider(): String? {
    val m = manager ?: return null
    val fine = appContext.checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
    if (Build.VERSION.SDK_INT >= 31 && m.hasProvider(LocationManager.FUSED_PROVIDER) && m.isProviderEnabled(LocationManager.FUSED_PROVIDER)) {
      return LocationManager.FUSED_PROVIDER
    }
    if (fine && m.isProviderEnabled(LocationManager.GPS_PROVIDER)) return LocationManager.GPS_PROVIDER
    if (m.isProviderEnabled(LocationManager.NETWORK_PROVIDER)) return LocationManager.NETWORK_PROVIDER
    return null
  }

  /**
   * L'ultima posizione conosciuta, se c'e' e se l'app ha il permesso: puo' essere vecchia (di ore o di giorni), e lo dice
   * [FluidMapUserLocation.timeMillis].
   */
  @SuppressLint("MissingPermission")
  fun last(): FluidMapUserLocation? {
    if (!hasPermission()) return null
    val m = manager ?: return null
    val p = provider() ?: return null
    return runCatching { m.getLastKnownLocation(p) }.getOrNull()?.toFluid()
  }

  /**
   * Le posizioni, una ogni [intervalMillis] al piu', finche' qualcuno ascolta. Niente senza permesso o senza fornitore.
   * Sul thread principale (il `Looper` principale), come vuole lo stato della mappa.
   *
   * La prima e' l'ultima posizione conosciuta solo se e' recente (al piu' [LastKnownMaxAgeMillis]): una di ieri non entra
   * come se fosse adesso, e la mappa che segue non ci vola (R3). Senza, il punto arriva col primo rilevamento.
   */
  @SuppressLint("MissingPermission")
  fun updates(intervalMillis: Long = 2_000): Flow<FluidMapUserLocation> = callbackFlow {
    val m = manager
    val p = if (hasPermission()) provider() else null
    if (m == null || p == null) {
      awaitClose { }
      return@callbackFlow
    }
    runCatching { m.getLastKnownLocation(p) }.getOrNull()?.takeIf { it.ageMillis() <= LastKnownMaxAgeMillis }?.let { trySend(it.toFluid()) }
    val listener = LocationListener { location -> trySend(location.toFluid()) }
    m.requestLocationUpdates(p, intervalMillis, 0f, listener, Looper.getMainLooper())
    awaitClose { m.removeUpdates(listener) }
  }
}

/**
 * Tiene [state] sulla posizione vera mentre [enabled] (di solito: il permesso c'e' e la mappa si vede). Fuori dal
 * permesso non fa niente.
 */
@Composable
fun FluidSystemLocationEffect(state: FluidMapState, enabled: Boolean, intervalMillis: Long = 2_000) {
  val context = LocalContext.current
  val location = remember(context) { FluidSystemLocation(context) }
  LaunchedEffect(location, enabled, intervalMillis) {
    if (!enabled || !location.hasPermission()) return@LaunchedEffect
    location.updates(intervalMillis).collect { state.userLocation = it }
  }
}

private fun Location.toFluid(): FluidMapUserLocation = FluidMapUserLocation(
  point = FluidGeoPoint(latitude.coerceIn(-90.0, 90.0), longitude),
  accuracyMeters = if (hasAccuracy()) accuracy else null,
  headingDegrees = if (hasBearing()) bearing else null,
  timeMillis = time.takeIf { it > 0L },
)

/**
 * Quanto puo' essere vecchia l'ultima posizione conosciuta per entrare come posizione di adesso: 30 secondi, lo stesso
 * confine con cui Android dice «attuale» una posizione gia' in memoria (`MAX_CURRENT_LOCATION_AGE_MS` di
 * `LocationProviderManager` in AOSP, la regola di `getCurrentLocation`; fiducia media, letto a memoria del sorgente).
 */
internal const val LastKnownMaxAgeMillis: Long = 30_000L

/**
 * L'eta' della posizione: dall'orologio che non torna indietro (`elapsedRealtimeNanos`, come `getElapsedRealtimeAgeMillis`
 * di Android 13), e dall'ora di sistema se il fornitore non l'ha messo.
 */
private fun Location.ageMillis(): Long =
  if (elapsedRealtimeNanos > 0L) (SystemClock.elapsedRealtimeNanos() - elapsedRealtimeNanos) / 1_000_000L
  else System.currentTimeMillis() - time

/**
 * Le licenze di `engine-map`, da aggiungere alla pagina «Licenze e crediti»: MapLibre Native, i dati di OpenStreetMap,
 * il servizio di OpenFreeMap e lo stile di OpenMapTiles.
 *
 * ```kotlin
 * fluidLicensesSection(licences = FluidEngineLicenses + FluidMapLicenses)
 * ```
 */
val FluidMapLicenses: List<FluidLicense> = listOf(
  FluidLicense("MapLibre Native", "MapLibre contributors", "BSD-2-Clause",
    "https://github.com/maplibre/maplibre-native", "Il motore che disegna la mappa"),
  FluidLicense("OpenStreetMap", "© contributori di OpenStreetMap", "ODbL-1.0",
    "https://www.openstreetmap.org/copyright", "I dati della mappa"),
  FluidLicense("OpenFreeMap", "Hyperknot Software Kft.", "Servizio gratuito (termini d'uso)",
    "https://openfreemap.org/tos/", "Le tessere della mappa, senza chiave"),
  FluidLicense("OpenMapTiles", "OpenMapTiles contributors", "BSD-3-Clause; CC-BY-4.0",
    "https://www.openmaptiles.org/", "Lo schema e gli stili delle tessere"),
)
