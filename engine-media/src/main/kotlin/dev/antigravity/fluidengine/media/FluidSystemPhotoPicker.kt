package dev.antigravity.fluidengine.media

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.OpenableColumns
import androidx.activity.compose.ManagedActivityResultLauncher
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.platform.LocalContext
import dev.antigravity.fluidengine.ui.fluid.viewer.FluidFileInfo
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Il **Photo Picker di Android** (F8.14), dove basta: la brief di F8.14 dice «usa il Photo Picker di Android dove basta,
 * e la griglia dell'engine dove serve di piu'». Non chiede permessi (Android 13+, e con gli aggiornamenti di Play anche
 * prima), mostra le foto del telefono e quelle del cloud del telefono, e restituisce indirizzi leggibili dall'app.
 *
 * Basta ([suffices]) quando l'unica sorgente e' il telefono e l'app non ha dichiarato i permessi delle foto: la griglia
 * dell'engine servirebbe solo a mostrare le stesse foto, e per farlo chiederebbe un permesso pesante. Serve la griglia
 * dell'engine per una sorgente di un'altra app (MyCloud Gallery: la ricerca per significato, le foto solo nel backup),
 * per la scelta in ordine col numero, per il pannello degli allegati con le recenti.
 */
object FluidSystemPhotoPicker {
  /** L'id di sorgente dei [FluidPickedMedia] che vengono dal Photo Picker di Android. */
  const val SourceId: String = "android-photo-picker"

  /** Il Photo Picker di sistema c'e' su questo telefono (altrimenti androidx ripiega su un selettore di documenti). */
  fun isAvailable(context: Context): Boolean = ActivityResultContracts.PickVisualMedia.isPhotoPickerAvailable(context)

  /**
   * Vero se per [sources] il Photo Picker di Android basta: c'e' solo il telefono ([FluidDeviceMediaSource]) e l'app non
   * ha dichiarato nel manifest i permessi delle foto (quindi la griglia del telefono non potrebbe mostrare niente).
   */
  fun suffices(context: Context, sources: List<FluidMediaSource>): Boolean =
    sources.all { it is FluidDeviceMediaSource } && !declaresMediaPermissions(context)

  /** L'app dichiara nel manifest almeno un permesso delle foto. */
  fun declaresMediaPermissions(context: Context): Boolean {
    val requested = try {
      @Suppress("DEPRECATION")
      context.packageManager.getPackageInfo(context.packageName, PackageManager.GET_PERMISSIONS).requestedPermissions
    } catch (_: Exception) {
      null
    }.orEmpty().toSet()
    val media = buildList {
      add(Manifest.permission.READ_EXTERNAL_STORAGE)
      if (Build.VERSION.SDK_INT >= 33) {
        add(Manifest.permission.READ_MEDIA_IMAGES)
        add(Manifest.permission.READ_MEDIA_VIDEO)
      }
    }
    return media.any { it in requested }
  }

  internal fun visualType(request: FluidMediaRequest): ActivityResultContracts.PickVisualMedia.VisualMediaType {
    val single = request.mimeTypes?.singleOrNull()?.takeIf { !it.endsWith("/*") }
    if (single != null) return ActivityResultContracts.PickVisualMedia.SingleMimeType(single)
    val photo = FluidMediaKind.Photo in request.kinds
    val video = FluidMediaKind.Video in request.kinds
    return when {
      photo && !video -> ActivityResultContracts.PickVisualMedia.ImageOnly
      video && !photo -> ActivityResultContracts.PickVisualMedia.VideoOnly
      else -> ActivityResultContracts.PickVisualMedia.ImageAndVideo
    }
  }

  /** Le informazioni dei file scelti (nome, tipo, peso), lette fuori dal filo principale. */
  suspend fun describe(context: Context, uris: List<Uri>): List<FluidPickedMedia> = withContext(Dispatchers.IO) {
    uris.map { uri ->
      var name: String? = null
      var size: Long? = null
      try {
        context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME, OpenableColumns.SIZE), null, null, null)?.use { c ->
          if (c.moveToFirst()) {
            val n = c.getColumnIndex(OpenableColumns.DISPLAY_NAME)
            val s = c.getColumnIndex(OpenableColumns.SIZE)
            if (n >= 0 && !c.isNull(n)) name = c.getString(n)
            if (s >= 0 && !c.isNull(s)) size = c.getLong(s)
          }
        }
      } catch (_: Exception) {
      }
      val mime = try {
        context.contentResolver.getType(uri)
      } catch (_: Exception) {
        null
      }
      FluidPickedMedia(
        uri = uri,
        info = FluidFileInfo(name = name ?: uri.lastPathSegment.orEmpty(), mimeType = mime, sizeBytes = size),
        kind = FluidMediaKind.from(mime),
        source = SourceId,
      )
    }
  }
}

/** Il lanciatore del Photo Picker di Android per una richiesta ([rememberFluidSystemPhotoPicker]). */
@Stable
class FluidSystemPhotoPickerLauncher internal constructor(private val start: () -> Unit) {
  /** Apre il Photo Picker di Android. */
  fun launch() = start()
}

/**
 * Il Photo Picker di Android per [request] ([FluidSystemPhotoPicker]): uno o, con [FluidMediaRequest.limit] > 1, piu'
 * elementi (al piu' il tetto, che Android rispetta). [onResult] riceve i file scelti, nell'ordine di Android; niente se la
 * persona annulla.
 */
@Composable
fun rememberFluidSystemPhotoPicker(
  request: FluidMediaRequest,
  onResult: (List<FluidPickedMedia>) -> Unit,
): FluidSystemPhotoPickerLauncher {
  val context = LocalContext.current
  val scope = rememberCoroutineScope()
  val result by rememberUpdatedState(onResult)
  val deliver: (List<Uri>) -> Unit = { uris ->
    if (uris.isNotEmpty()) scope.launch { result(FluidSystemPhotoPicker.describe(context, uris)) }
  }
  val one: ManagedActivityResultLauncher<PickVisualMediaRequest, Uri?> =
    rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri -> deliver(listOfNotNull(uri)) }
  val many: ManagedActivityResultLauncher<PickVisualMediaRequest, List<Uri>> =
    rememberLauncherForActivityResult(ActivityResultContracts.PickMultipleVisualMedia(maxOf(2, request.limit))) { uris -> deliver(uris) }
  return remember(request, one, many) {
    FluidSystemPhotoPickerLauncher {
      val input = PickVisualMediaRequest(FluidSystemPhotoPicker.visualType(request))
      if (request.limit <= 1) one.launch(input) else many.launch(input)
    }
  }
}
