/*
 * engine-map (4.0, F8.26): la cartolina, una mappa piccola e ferma (`MKMapSnapshotter`), e il luogo per il pannello info
 * dei visori. Codice del Fluid Engine.
 */
package dev.antigravity.fluidengine.map

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.os.Build
import android.util.LruCache
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.BasicText
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.antigravity.fluidengine.ui.fluid.ContinuousCornerShape
import dev.antigravity.fluidengine.ui.fluid.FluidAsyncImage
import dev.antigravity.fluidengine.ui.fluid.FluidImagePhase
import dev.antigravity.fluidengine.ui.fluid.FluidRadius
import dev.antigravity.fluidengine.ui.fluid.viewer.FluidViewerLocation
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.security.MessageDigest
import java.util.Locale

/** I numeri della cartolina. */
object FluidMapPostcardDefaults {
  /** Lo zoom di serie: un quartiere, come la mappa del pannello info di Foto. A occhio, 26/11/2026. */
  const val Zoom: Double = 14.0

  /** L'altezza nel pannello info dei visori: 160 dp, la mappa di «Informazioni» di Foto in iOS 26. A occhio, 26/11/2026. */
  val ViewerHeight: Dp = 160.dp
}

/**
 * La cartolina: una mappa **piccola e ferma** di [point] (`MKMapSnapshotter`, la mappa del pannello info di Foto), col
 * segnaposto al centro e l'attribuzione nell'angolo. Si mette in una riga o nel pannello info di un visore
 * ([fluidViewerLocation]); toccata chiama [onClick] (di solito apre la mappa intera: con `fluidZoomSource` sulla
 * cartolina la mappa nasce da lei).
 *
 * **Ferma vuol dire che non costa.** L'immagine si fa una volta, fuori dal thread principale, e si tiene in memoria e su
 * disco ([FluidMapSnapshots]): scorrendo un elenco di foto la cartolina non rifà niente, e non c'e' nessuna mappa viva. Al
 * primo arrivo entra sfocata e si mette a fuoco ([FluidAsyncImage]: e' contenuto che arriva da solo); dalla cache c'e' gia'.
 * Senza rete, il colore dello stile e il segnaposto (il luogo resta leggibile).
 *
 * TalkBack la legge come «Mappa di [title]» (o delle coordinate), con l'azione [FluidMapLabels.openMap].
 */
@Composable
fun FluidMapPostcard(
  point: FluidGeoPoint,
  modifier: Modifier = Modifier,
  title: String? = null,
  zoom: Double = FluidMapPostcardDefaults.Zoom,
  style: FluidMapStyle = FluidMapStyles.Explore,
  engine: FluidMapEngine = rememberFluidMapEngine(),
  shape: Shape = ContinuousCornerShape(FluidRadius.Card),
  markerTint: Color = Color.Unspecified,
  labels: FluidMapLabels = FluidMapLabels(),
  onClick: (() -> Unit)? = null,
) {
  val context = LocalContext.current
  val density = LocalDensity.current.density
  val dark = MaterialTheme.colorScheme.surface.luminance() < 0.5f
  val description = labels.postcard(title ?: FluidGeoFormat.display(point))
  BoxWithConstraints(
    modifier
      .clip(shape)
      .background(if (dark) style.darkSwatch else style.swatch)
      .testTag(FluidMapTags.Postcard)
      .semantics(mergeDescendants = true) {
        contentDescription = description
        if (onClick != null) {
          role = Role.Button
          onClick(label = labels.openMap) { onClick(); true }
        }
      }
      .then(if (onClick != null) Modifier.clickable(onClickLabel = labels.openMap, onClick = onClick) else Modifier),
  ) {
    val widthPx = (constraints.maxWidth.takeIf { it != androidx.compose.ui.unit.Constraints.Infinity } ?: 0)
    val heightPx = (constraints.maxHeight.takeIf { it != androidx.compose.ui.unit.Constraints.Infinity } ?: 0)
    if (widthPx > 0 && heightPx > 0) {
      val request = remember(point, zoom, style, dark, widthPx, heightPx, density) {
        FluidMapSnapshotRequest(FluidMapCamera(point, zoom), widthPx, heightPx, density, style, dark)
      }
      val cached = remember(request) { FluidMapSnapshots.cached(request) }
      val phase by produceState<FluidImagePhase>(
        initialValue = cached?.let { FluidImagePhase.Success(it) } ?: FluidImagePhase.Loading,
        request, engine,
      ) {
        if (cached != null) return@produceState
        val image = FluidMapSnapshots.get(engine, request, context)
        value = if (image != null) FluidImagePhase.Success(image) else FluidImagePhase.Failure()
      }
      if (phase !is FluidImagePhase.Failure) {
        FluidAsyncImage(phase = phase, contentDescription = null, modifier = Modifier.fillMaxSize())
      }
    }
    // Il segnaposto al centro: la punta sul punto.
    Box(Modifier.align(Alignment.Center).offset(y = -(FluidMapPinDefaults.Balloon + FluidMapPinDefaults.Tail) / 2)) {
      PostcardPin(markerTint)
    }
    BasicText(
      style.attribution.short,
      modifier = Modifier.align(Alignment.BottomStart).padding(start = 6.dp, bottom = 4.dp),
      style = TextStyle(
        color = if (dark) Color.White.copy(alpha = 0.78f) else Color.Black.copy(alpha = 0.62f),
        fontSize = 9.sp,
        fontWeight = FontWeight.Medium,
      ),
      maxLines = 1,
    )
  }
}

@Composable
private fun PostcardPin(tint: Color) {
  // Lo stesso pallino della mappa, senza nome e senza tocco: la cartolina intera e' il tasto.
  val marker = remember(tint) { FluidMapMarker("cartolina", FluidGeoPoint(0.0, 0.0), "", tint = tint) }
  FluidMapBalloon(marker)
}

/**
 * Il luogo di un file per il pannello info dei visori ([FluidViewerLocation]): le coordinate come le scrive Mappe, la
 * [FluidMapPostcard] alta [FluidMapPostcardDefaults.ViewerHeight] al posto della mappa che prima disegnava l'app, e
 * «Apri la mappa» ([onOpen]) nelle azioni del pannello e sul tocco della cartolina.
 *
 * ```kotlin
 * FluidViewerInfo(file = info, location = fluidViewerLocation(FluidGeoPoint(43.7731, 11.2556), "Firenze") { apriMappa() })
 * ```
 */
fun fluidViewerLocation(
  point: FluidGeoPoint,
  title: String? = null,
  zoom: Double = FluidMapPostcardDefaults.Zoom,
  style: FluidMapStyle = FluidMapStyles.Explore,
  labels: FluidMapLabels = FluidMapLabels(),
  onOpen: (() -> Unit)? = null,
): FluidViewerLocation = FluidViewerLocation(
  coordinates = FluidGeoFormat.display(point),
  map = {
    FluidMapPostcard(
      point = point,
      modifier = Modifier
        .padding(horizontal = 16.dp)
        .padding(top = 10.dp)
        .fillMaxWidth()
        .height(FluidMapPostcardDefaults.ViewerHeight),
      title = title,
      zoom = zoom,
      style = style,
      labels = labels,
      onClick = onOpen,
    )
  },
  openMap = onOpen,
)

/**
 * La cache delle cartoline: in memoria (un sedicesimo della memoria dell'app, al piu' 16 MiB: una decina di cartoline grandi) e su
 * disco, nella cache dell'app (al piu' [DiskEntries] file, i piu' vecchi vanno via). La chiave e' lo stile, il tema, il
 * punto a cinque decimali, lo zoom e la misura: la stessa cartolina in un altro elenco non si rifa'.
 */
object FluidMapSnapshots {
  /** Quante cartoline restano su disco. */
  const val DiskEntries: Int = 240

  private val memory = object : LruCache<String, ImageBitmap>(
    minOf(16 * 1024 * 1024L, Runtime.getRuntime().maxMemory() / 16).toInt().coerceAtLeast(1),
  ) {
    override fun sizeOf(key: String, value: ImageBitmap): Int = value.asAndroidBitmap().byteCount
  }

  /** La cartolina di [request] se e' gia' in memoria. */
  fun cached(request: FluidMapSnapshotRequest): ImageBitmap? = memory.get(keyOf(request))

  /**
   * La cartolina di [request]: dalla memoria, dal disco o dal [engine] (che la fa), e poi tenuta. Null se il motore non
   * la puo' fare (niente rete). Lavora fuori dal thread principale, tranne il motore, che sceglie lui.
   */
  suspend fun get(engine: FluidMapEngine, request: FluidMapSnapshotRequest, context: Context? = null): ImageBitmap? {
    val key = keyOf(request)
    memory.get(key)?.let { return it }
    val dir = context?.let { File(it.cacheDir, "fluid-map") }
    val file = dir?.let { File(it, "${digest(key)}.img") }
    if (file != null) {
      val fromDisk = withContext(Dispatchers.IO) {
        if (file.exists()) runCatching { BitmapFactory.decodeFile(file.path)?.asImageBitmap() }.getOrNull() else null
      }
      if (fromDisk != null) {
        memory.put(key, fromDisk)
        return fromDisk
      }
    }
    val made = runCatching { engine.snapshot(request) }.getOrNull() ?: return null
    memory.put(key, made)
    if (file != null) {
      withContext(Dispatchers.IO) {
        runCatching {
          val folder = file.parentFile ?: return@runCatching
          folder.mkdirs()
          val tmp = File(folder, "${file.name}.tmp")
          tmp.outputStream().use { out ->
            @Suppress("DEPRECATION")
            val format = if (Build.VERSION.SDK_INT >= 30) Bitmap.CompressFormat.WEBP_LOSSY else Bitmap.CompressFormat.WEBP
            made.asAndroidBitmap().compress(format, 90, out)
          }
          tmp.renameTo(file)
          trim(folder)
        }
      }
    }
    return made
  }

  /** Svuota la memoria (il disco resta: lo pulisce il sistema con la cache dell'app). */
  fun clearMemory() {
    memory.evictAll()
  }

  private fun trim(dir: File) {
    val files = dir.listFiles { f -> f.name.endsWith(".img") } ?: return
    if (files.size <= DiskEntries) return
    files.sortedBy { it.lastModified() }.take(files.size - DiskEntries).forEach { it.delete() }
  }

  internal fun keyOf(r: FluidMapSnapshotRequest): String = String.format(
    Locale.ROOT, "%s|%s|%.5f|%.5f|%.2f|%.1f|%dx%d|%.2f",
    r.style.id, if (r.dark) "scuro" else "chiaro", r.camera.center.latitude, r.camera.center.wrappedLongitude,
    r.camera.zoom, r.camera.normalizedBearing, r.widthPx, r.heightPx, r.density,
  )

  private fun digest(key: String): String =
    MessageDigest.getInstance("SHA-256").digest(key.toByteArray()).joinToString("") { "%02x".format(it) }.take(32)
}
