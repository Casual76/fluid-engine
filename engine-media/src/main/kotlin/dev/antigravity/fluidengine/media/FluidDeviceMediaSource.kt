package dev.antigravity.fluidengine.media

import android.Manifest
import android.content.ContentResolver
import android.content.ContentUris
import android.content.Context
import android.content.pm.PackageManager
import android.database.Cursor
import android.graphics.Bitmap
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.CancellationSignal
import android.provider.MediaStore
import android.util.Size
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.core.content.ContextCompat
import dev.antigravity.fluidengine.ui.fluid.viewer.FluidFileInfo
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import kotlin.coroutines.coroutineContext

/**
 * Le foto e i video del telefono, dal MediaStore (F8.14). Coi tre regimi dei permessi delle foto di Android:
 * - **tutto** (`READ_MEDIA_IMAGES` e `READ_MEDIA_VIDEO` da Android 13, `READ_EXTERNAL_STORAGE` prima): la libreria intera;
 * - **solo alcune** (`READ_MEDIA_VISUAL_USER_SELECTED` da solo, Android 14): il MediaStore da' da se' solo quelle
 *   concesse, e l'accesso e' [FluidMediaAccess.Limited]: il selettore lo dice e offre di sceglierne altre;
 * - **niente**: [FluidMediaAccess.NotAllowed], niente si elenca; il selettore offre il tasto del permesso (dal tasto, con
 *   `rememberFluidPermission` di F8.11) e il Photo Picker di Android, che non chiede permessi.
 *
 * L'accesso si rilegge da solo a ogni [refresh] (il selettore lo chiama dopo il dialogo e al ritorno dalle impostazioni).
 *
 * **I permessi li dichiara l'app**, nel suo manifest (il modulo non ne porta):
 *
 * ```xml
 * <uses-permission android:name="android.permission.READ_MEDIA_IMAGES" />
 * <uses-permission android:name="android.permission.READ_MEDIA_VIDEO" />
 * <uses-permission android:name="android.permission.READ_MEDIA_VISUAL_USER_SELECTED" />
 * <uses-permission android:name="android.permission.READ_EXTERNAL_STORAGE" android:maxSdkVersion="32" />
 * ```
 *
 * Gli album sono quelli di Foto: Recenti, Preferiti (da Android 11), Video, Screenshot, poi le cartelle (Fotocamera per
 * prima). La ricerca e' per nome del file e della cartella, non per significato. La consegna da' gli indirizzi del
 * MediaStore, che l'app legge col suo permesso.
 *
 * @param kinds i tipi che la sorgente elenca e per cui chiede l'accesso (di serie foto e video).
 */
@Stable
class FluidDeviceMediaSource(
  context: Context,
  private val kinds: Set<FluidMediaKind> = FluidMediaKind.PhotosAndVideos,
  override val label: String = "Su questo telefono",
) : FluidMediaSource {

  private val context: Context = context.applicationContext ?: context
  private val resolver: ContentResolver get() = context.contentResolver

  override val id: String = SourceId

  override val capabilities: FluidMediaSourceCapabilities =
    FluidMediaSourceCapabilities(search = true, semanticSearch = false, albums = true, backupOnly = false)

  override var access: FluidMediaAccess by mutableStateOf(readAccess())
    private set

  override suspend fun refresh() {
    access = readAccess()
  }

  private fun readAccess(): FluidMediaAccess =
    fluidDeviceMediaAccess(Build.VERSION.SDK_INT, kinds) { permission ->
      ContextCompat.checkSelfPermission(context, permission) == PackageManager.PERMISSION_GRANTED
    }

  override suspend fun albums(): List<FluidMediaAlbum> = withContext(Dispatchers.IO) {
    if (!access.allowsListing()) return@withContext emptyList()
    val rows = ArrayList<DeviceRow>()
    query(selection = kindSelection(kinds), args = emptyArray(), sort = SortNewest, limit = null, offset = 0) { cursor ->
      val columns = DeviceColumns(cursor)
      while (cursor.moveToNext()) {
        rows += columns.row(cursor)
        if (rows.size % 512 == 0) coroutineContext.ensureActive()
      }
    }
    fluidDeviceAlbums(rows, kinds, Build.VERSION.SDK_INT >= 30)
  }

  override suspend fun page(query: FluidMediaQuery, cursor: String?, limit: Int): FluidMediaPage = withContext(Dispatchers.IO) {
    if (!access.allowsListing()) return@withContext FluidMediaPage(emptyList())
    val wanted = query.kinds.intersect(kinds).ifEmpty { return@withContext FluidMediaPage(emptyList()) }
    val (selection, args) = fluidDeviceSelection(wanted, query.album, query.text)
    val offset = cursor?.toIntOrNull()?.coerceAtLeast(0) ?: 0
    val items = ArrayList<FluidMediaItem>(limit)
    var rows = 0
    query(selection, args, SortNewest, limit + 1, offset) { c ->
      val columns = DeviceColumns(c)
      while (c.moveToNext() && rows < limit + 1) {
        rows++
        if (items.size < limit) items += columns.row(c).item()
      }
    }
    FluidMediaPage(items = items, next = if (rows > limit) (offset + limit).toString() else null)
  }

  override suspend fun thumbnail(item: FluidMediaItem, sizePx: Int): Bitmap? = withContext(Dispatchers.IO) {
    val id = item.id.toLongOrNull() ?: return@withContext null
    val signal = CancellationSignal()
    try {
      if (Build.VERSION.SDK_INT >= 29) {
        resolver.loadThumbnail(contentUri(item.kind, id), Size(sizePx, sizePx), signal)
      } else {
        @Suppress("DEPRECATION")
        if (item.kind == FluidMediaKind.Video) {
          MediaStore.Video.Thumbnails.getThumbnail(resolver, id, MediaStore.Video.Thumbnails.MINI_KIND, null)
        } else {
          MediaStore.Images.Thumbnails.getThumbnail(resolver, id, MediaStore.Images.Thumbnails.MINI_KIND, null)
        }
      }
    } catch (_: Exception) {
      null
    }
  }

  override suspend fun deliver(
    items: List<FluidMediaItem>,
    request: FluidMediaRequest,
    onProgress: (FluidMediaProgress) -> Unit,
  ): FluidMediaDelivery = withContext(Dispatchers.IO) {
    if (!access.allowsListing()) return@withContext FluidMediaDelivery.Refused(FluidMediaRefusal.NothingAvailable)
    var skipped = 0
    val out = ArrayList<FluidPickedMedia>(items.size)
    items.forEachIndexed { index, item ->
      coroutineContext.ensureActive()
      onProgress(FluidMediaProgress(index, items.size))
      val id = item.id.toLongOrNull()
      // La vista di adesso decide: l'elemento deve esserci ancora, e leggibile da questa app.
      val row = id?.let { readOne(item.kind, it) }
      if (row == null || !request.accepts(row.item())) {
        skipped++
        return@forEachIndexed
      }
      val uri = contentUri(row.kind, row.id)
      out += FluidPickedMedia(
        uri = uri,
        info = FluidFileInfo(name = row.name ?: "", mimeType = row.mime, sizeBytes = row.size, modifiedAtMillis = row.taken),
        kind = row.kind,
        width = row.width,
        height = row.height,
        durationMillis = row.duration,
        source = SourceId,
        item = row.item(),
      )
    }
    onProgress(FluidMediaProgress(items.size, items.size))
    if (out.isEmpty()) FluidMediaDelivery.Refused(FluidMediaRefusal.NothingAvailable) else FluidMediaDelivery.Ready(out, skipped)
  }

  private fun readOne(kind: FluidMediaKind, id: Long): DeviceRow? {
    var row: DeviceRow? = null
    query("${MediaStore.MediaColumns._ID} = ?", arrayOf(id.toString()), null, 1, 0) { c ->
      if (c.moveToFirst()) row = DeviceColumns(c).row(c)
    }
    return row?.takeIf { it.kind == kind }
  }

  private inline fun query(
    selection: String,
    args: Array<String>,
    sort: String?,
    limit: Int?,
    offset: Int,
    read: (Cursor) -> Unit,
  ) {
    val uri = filesUri()
    val cursor = try {
      if (Build.VERSION.SDK_INT >= 30) {
        val bundle = Bundle().apply {
          putString(ContentResolver.QUERY_ARG_SQL_SELECTION, selection)
          putStringArray(ContentResolver.QUERY_ARG_SQL_SELECTION_ARGS, args)
          if (sort != null) putString(ContentResolver.QUERY_ARG_SQL_SORT_ORDER, sort)
          if (limit != null) {
            putInt(ContentResolver.QUERY_ARG_LIMIT, limit)
            putInt(ContentResolver.QUERY_ARG_OFFSET, offset)
          }
        }
        resolver.query(uri, Projection, bundle, null)
      } else {
        val order = buildString {
          append(sort ?: "${MediaStore.MediaColumns._ID} DESC")
          if (limit != null) append(" LIMIT $limit OFFSET $offset")
        }
        resolver.query(uri, Projection, selection, args, order)
      }
    } catch (_: Exception) {
      null
    }
    cursor?.use(read)
  }

  override fun toString(): String = "FluidDeviceMediaSource"

  companion object {
    /** L'id della sorgente del telefono. */
    const val SourceId: String = "device"

    /** L'indirizzo di un elemento del MediaStore. */
    fun contentUri(kind: FluidMediaKind, id: Long): Uri = ContentUris.withAppendedId(
      if (kind == FluidMediaKind.Video) MediaStore.Video.Media.EXTERNAL_CONTENT_URI else MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
      id,
    )

    internal fun filesUri(): Uri =
      if (Build.VERSION.SDK_INT >= 29) MediaStore.Files.getContentUri(MediaStore.VOLUME_EXTERNAL) else MediaStore.Files.getContentUri("external")

    internal const val ColumnDuration = "duration"
    internal const val ColumnTaken = "datetaken"
    internal const val ColumnBucketId = "bucket_id"
    internal const val ColumnBucketName = "bucket_display_name"
    internal const val ColumnFavorite = "is_favorite"
    internal const val ColumnRelativePath = "relative_path"

    internal val Projection: Array<String> = buildList {
      add(MediaStore.MediaColumns._ID)
      add(MediaStore.Files.FileColumns.MEDIA_TYPE)
      add(MediaStore.MediaColumns.MIME_TYPE)
      add(MediaStore.MediaColumns.WIDTH)
      add(MediaStore.MediaColumns.HEIGHT)
      add(ColumnDuration)
      add(ColumnTaken)
      add(MediaStore.MediaColumns.DATE_ADDED)
      add(MediaStore.MediaColumns.SIZE)
      add(MediaStore.MediaColumns.DISPLAY_NAME)
      add(ColumnBucketId)
      add(ColumnBucketName)
      if (Build.VERSION.SDK_INT >= 30) add(ColumnFavorite)
      if (Build.VERSION.SDK_INT >= 29) add(ColumnRelativePath)
    }.toTypedArray()

    /** Dal piu' recente: la data dello scatto, poi quella d'arrivo, poi l'id. */
    internal const val SortNewest = "$ColumnTaken DESC, ${MediaStore.MediaColumns.DATE_ADDED} DESC, ${MediaStore.MediaColumns._ID} DESC"
  }
}

/** Una riga del MediaStore, come la legge la sorgente del telefono. */
internal data class DeviceRow(
  val id: Long,
  val kind: FluidMediaKind,
  val mime: String?,
  val width: Int?,
  val height: Int?,
  val duration: Long?,
  val taken: Long?,
  val size: Long?,
  val name: String?,
  val bucketId: String?,
  val bucketName: String?,
  val favorite: Boolean,
  val relativePath: String? = null,
) {
  fun item(): FluidMediaItem = FluidMediaItem(
    source = FluidDeviceMediaSource.SourceId,
    id = id.toString(),
    kind = kind,
    mimeType = mime,
    width = width,
    height = height,
    durationMillis = duration,
    takenAtMillis = taken,
    sizeBytes = size,
    name = name,
    favorite = favorite,
  )

  /** Uno screenshot: la cartella «Screenshots» (o «Screenshot», come la chiamano alcuni produttori). */
  val screenshot: Boolean
    get() = (bucketName ?: "").contains("screenshot", ignoreCase = true) ||
      (relativePath ?: "").contains("screenshot", ignoreCase = true)

  /** La cartella della fotocamera (`DCIM/Camera`). */
  val camera: Boolean
    get() = (bucketName ?: "").equals("Camera", ignoreCase = true) || (relativePath ?: "").startsWith("DCIM/Camera", ignoreCase = true)
}

private class DeviceColumns(cursor: Cursor) {
  val id = cursor.getColumnIndex(MediaStore.MediaColumns._ID)
  val type = cursor.getColumnIndex(MediaStore.Files.FileColumns.MEDIA_TYPE)
  val mime = cursor.getColumnIndex(MediaStore.MediaColumns.MIME_TYPE)
  val width = cursor.getColumnIndex(MediaStore.MediaColumns.WIDTH)
  val height = cursor.getColumnIndex(MediaStore.MediaColumns.HEIGHT)
  val duration = cursor.getColumnIndex(FluidDeviceMediaSource.ColumnDuration)
  val taken = cursor.getColumnIndex(FluidDeviceMediaSource.ColumnTaken)
  val added = cursor.getColumnIndex(MediaStore.MediaColumns.DATE_ADDED)
  val size = cursor.getColumnIndex(MediaStore.MediaColumns.SIZE)
  val name = cursor.getColumnIndex(MediaStore.MediaColumns.DISPLAY_NAME)
  val bucketId = cursor.getColumnIndex(FluidDeviceMediaSource.ColumnBucketId)
  val bucketName = cursor.getColumnIndex(FluidDeviceMediaSource.ColumnBucketName)
  val favorite = cursor.getColumnIndex(FluidDeviceMediaSource.ColumnFavorite)
  val relative = cursor.getColumnIndex(FluidDeviceMediaSource.ColumnRelativePath)

  fun row(c: Cursor): DeviceRow {
    val mimeType = c.str(mime)
    val video = c.int(type) == MediaStore.Files.FileColumns.MEDIA_TYPE_VIDEO || mimeType?.startsWith("video/") == true
    val takenMillis = c.long(taken)?.takeIf { it > 0 } ?: c.long(added)?.takeIf { it > 0 }?.times(1000)
    return DeviceRow(
      id = c.long(id) ?: 0L,
      kind = if (video) FluidMediaKind.Video else FluidMediaKind.Photo,
      mime = mimeType,
      width = c.int(width)?.takeIf { it > 0 },
      height = c.int(height)?.takeIf { it > 0 },
      duration = if (video) c.long(duration)?.takeIf { it >= 0 } else null,
      taken = takenMillis,
      size = c.long(size)?.takeIf { it >= 0 },
      name = c.str(name),
      bucketId = c.str(bucketId),
      bucketName = c.str(bucketName),
      favorite = (c.int(favorite) ?: 0) == 1,
      relativePath = c.str(relative),
    )
  }

  private fun Cursor.str(i: Int): String? = if (i < 0 || isNull(i)) null else getString(i)
  private fun Cursor.int(i: Int): Int? = if (i < 0 || isNull(i)) null else getInt(i)
  private fun Cursor.long(i: Int): Long? = if (i < 0 || isNull(i)) null else getLong(i)
}

/**
 * L'accesso alle foto del telefono, da cosa concede Android ([granted]) sulla versione [sdk] per i tipi [kinds]. Puro, per
 * le prove dei tre regimi.
 */
internal fun fluidDeviceMediaAccess(sdk: Int, kinds: Set<FluidMediaKind>, granted: (String) -> Boolean): FluidMediaAccess {
  if (sdk < 33) {
    return if (granted(Manifest.permission.READ_EXTERNAL_STORAGE)) FluidMediaAccess.Full else FluidMediaAccess.NotAllowed()
  }
  val full = buildList {
    if (FluidMediaKind.Photo in kinds) add(Manifest.permission.READ_MEDIA_IMAGES)
    if (FluidMediaKind.Video in kinds) add(Manifest.permission.READ_MEDIA_VIDEO)
  }
  if (full.isNotEmpty() && full.all(granted)) return FluidMediaAccess.Full
  if (sdk >= 34 && granted(SelectedPermission)) return FluidMediaAccess.Limited
  return FluidMediaAccess.NotAllowed()
}

private const val SelectedPermission = "android.permission.READ_MEDIA_VISUAL_USER_SELECTED"

/** La condizione SQL sui tipi. */
private fun kindSelection(kinds: Set<FluidMediaKind>): String {
  val types = buildList {
    if (FluidMediaKind.Photo in kinds) add(MediaStore.Files.FileColumns.MEDIA_TYPE_IMAGE)
    if (FluidMediaKind.Video in kinds) add(MediaStore.Files.FileColumns.MEDIA_TYPE_VIDEO)
  }
  return "${MediaStore.Files.FileColumns.MEDIA_TYPE} IN (${types.joinToString(",").ifEmpty { "-1" }})"
}

/**
 * La condizione SQL di una pagina: i tipi, l'album ([album] e' un id di [fluidDeviceAlbums]) e il testo (per nome del file
 * e della cartella). Il testo passa sempre come argomento, mai dentro l'SQL.
 */
internal fun fluidDeviceSelection(kinds: Set<FluidMediaKind>, album: String?, text: String?): Pair<String, Array<String>> {
  val parts = arrayListOf(kindSelection(kinds))
  val args = ArrayList<String>()
  when {
    album == null || album == DeviceAlbumAll -> Unit
    album == DeviceAlbumFavorites -> parts += "${FluidDeviceMediaSource.ColumnFavorite} = 1"
    album == DeviceAlbumVideos -> parts += "${MediaStore.Files.FileColumns.MEDIA_TYPE} = ${MediaStore.Files.FileColumns.MEDIA_TYPE_VIDEO}"
    album == DeviceAlbumScreenshots -> parts += "(${FluidDeviceMediaSource.ColumnBucketName} LIKE '%creenshot%')"
    album.startsWith(DeviceBucketPrefix) -> {
      parts += "${FluidDeviceMediaSource.ColumnBucketId} = ?"
      args += album.removePrefix(DeviceBucketPrefix)
    }
  }
  val words = text?.trim()?.split(Regex("\\s+"))?.filter { it.isNotEmpty() }?.take(6).orEmpty()
  for (word in words) {
    parts += "(${MediaStore.MediaColumns.DISPLAY_NAME} LIKE ? ESCAPE '\\' OR ${FluidDeviceMediaSource.ColumnBucketName} LIKE ? ESCAPE '\\')"
    val like = "%" + word.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_") + "%"
    args += like
    args += like
  }
  return parts.joinToString(" AND ") to args.toTypedArray()
}

internal const val DeviceAlbumAll = "all"
internal const val DeviceAlbumFavorites = "favorites"
internal const val DeviceAlbumVideos = "videos"
internal const val DeviceAlbumScreenshots = "screenshots"
internal const val DeviceBucketPrefix = "bucket:"

/**
 * Gli album del telefono dalle righe (dal piu' recente): Recenti, Preferiti (se [favorites] e ce ne sono), Video (se ce ne
 * sono e i [kinds] li vogliono), Screenshot, poi le cartelle per numero di elementi, la Fotocamera per prima. Le parole
 * sono quelle di Foto. Pura, per le prove.
 */
internal fun fluidDeviceAlbums(rows: List<DeviceRow>, kinds: Set<FluidMediaKind>, favorites: Boolean): List<FluidMediaAlbum> {
  val source = FluidDeviceMediaSource.SourceId
  if (rows.isEmpty()) return listOf(FluidMediaAlbum(source, DeviceAlbumAll, "Recenti", 0, null, FluidMediaAlbumRole.All))
  val out = ArrayList<FluidMediaAlbum>()
  out += FluidMediaAlbum(source, DeviceAlbumAll, "Recenti", rows.size, rows.first().item(), FluidMediaAlbumRole.All)
  if (favorites) {
    val fav = rows.filter { it.favorite }
    if (fav.isNotEmpty()) out += FluidMediaAlbum(source, DeviceAlbumFavorites, "Preferiti", fav.size, fav.first().item(), FluidMediaAlbumRole.Favorites)
  }
  if (FluidMediaKind.Video in kinds) {
    val videos = rows.filter { it.kind == FluidMediaKind.Video }
    if (videos.isNotEmpty() && videos.size < rows.size) {
      out += FluidMediaAlbum(source, DeviceAlbumVideos, "Video", videos.size, videos.first().item(), FluidMediaAlbumRole.Videos)
    }
  }
  val shots = rows.filter { it.screenshot }
  if (shots.isNotEmpty()) out += FluidMediaAlbum(source, DeviceAlbumScreenshots, "Screenshot", shots.size, shots.first().item(), FluidMediaAlbumRole.Screenshots)
  val buckets = rows.filter { it.bucketId != null && !it.screenshot }.groupBy { it.bucketId!! }
  val ordered = buckets.entries.sortedWith(compareByDescending<Map.Entry<String, List<DeviceRow>>> { e -> e.value.first().camera }.thenByDescending { it.value.size })
  for ((bucket, list) in ordered) {
    val first = list.first()
    val camera = first.camera
    out += FluidMediaAlbum(
      source = source,
      id = DeviceBucketPrefix + bucket,
      title = if (camera) "Fotocamera" else first.bucketName ?: "Cartella",
      count = list.size,
      cover = first.item(),
      role = if (camera) FluidMediaAlbumRole.Camera else FluidMediaAlbumRole.User,
    )
  }
  return out
}
