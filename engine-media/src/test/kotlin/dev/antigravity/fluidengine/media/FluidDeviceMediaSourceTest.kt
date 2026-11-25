package dev.antigravity.fluidengine.media

import android.Manifest
import android.app.Application
import android.content.ContentProvider
import android.content.ContentValues
import android.content.pm.ProviderInfo
import android.database.Cursor
import android.database.MatrixCursor
import android.net.Uri
import android.os.Bundle
import android.os.CancellationSignal
import android.provider.MediaStore
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

/**
 * Un MediaStore finto: le righe di [rows], col limite e lo spostamento della domanda di Android 11+, e la condizione
 * sull'id (la consegna rilegge l'elemento). Ricorda le domande per le prove.
 */
class FakeMediaStore : ContentProvider() {
  companion object {
    val rows = ArrayList<Array<Any?>>()
    val asked = ArrayList<Bundle?>()

    fun row(id: Long, video: Boolean, taken: Long, bucket: String, name: String = "IMG_$id.jpg", favorite: Boolean = false): Array<Any?> =
      arrayOf(
        id,
        if (video) MediaStore.Files.FileColumns.MEDIA_TYPE_VIDEO else MediaStore.Files.FileColumns.MEDIA_TYPE_IMAGE,
        if (video) "video/mp4" else "image/jpeg",
        4032, 3024, if (video) 65_000L else null, taken, taken / 1000, 1_000_000L, name, bucket.hashCode().toString(), bucket,
        if (favorite) 1 else 0, "DCIM/$bucket/",
      )
  }

  override fun onCreate(): Boolean = true

  override fun query(uri: Uri, projection: Array<out String>?, queryArgs: Bundle?, cancellationSignal: CancellationSignal?): Cursor {
    asked += queryArgs
    val columns = FluidDeviceMediaSource.Projection
    val cursor = MatrixCursor(columns)
    val selection = queryArgs?.getString(android.content.ContentResolver.QUERY_ARG_SQL_SELECTION).orEmpty()
    val args = queryArgs?.getStringArray(android.content.ContentResolver.QUERY_ARG_SQL_SELECTION_ARGS).orEmpty()
    var list: List<Array<Any?>> = rows
    if (selection.startsWith("${MediaStore.MediaColumns._ID} = ?")) list = list.filter { it[0].toString() == args.first() }
    if (selection.contains("${FluidDeviceMediaSource.ColumnBucketId} = ?")) list = list.filter { it[10] == args.first() }
    if (selection.contains("${MediaStore.Files.FileColumns.MEDIA_TYPE} = ${MediaStore.Files.FileColumns.MEDIA_TYPE_VIDEO}")) {
      list = list.filter { it[1] == MediaStore.Files.FileColumns.MEDIA_TYPE_VIDEO }
    }
    val offset = queryArgs?.getInt(android.content.ContentResolver.QUERY_ARG_OFFSET, 0) ?: 0
    val limit = queryArgs?.getInt(android.content.ContentResolver.QUERY_ARG_LIMIT, Int.MAX_VALUE) ?: Int.MAX_VALUE
    list.drop(offset).take(limit).forEach { cursor.addRow(it.copyOf(columns.size)) }
    return cursor
  }

  override fun query(uri: Uri, projection: Array<out String>?, selection: String?, selectionArgs: Array<out String>?, sortOrder: String?): Cursor? =
    query(uri, projection, Bundle(), null)

  override fun getType(uri: Uri): String? = null
  override fun insert(uri: Uri, values: ContentValues?): Uri? = null
  override fun delete(uri: Uri, selection: String?, selectionArgs: Array<out String>?): Int = 0
  override fun update(uri: Uri, values: ContentValues?, selection: String?, selectionArgs: Array<out String>?): Int = 0
}

/**
 * F8.14 · Le foto del telefono coi tre regimi dei permessi (tutto, «solo alcune» di Android 14, niente), gli album come
 * Foto e la condizione della ricerca, contro un MediaStore finto registrato in Robolectric.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class FluidDeviceMediaSourceTest {
  private val app: Application = ApplicationProvider.getApplicationContext()

  @Before
  fun store() {
    FakeMediaStore.rows.clear()
    FakeMediaStore.asked.clear()
    val day = 86_400_000L
    val newest = 1_764_000_000_000L
    FakeMediaStore.rows += FakeMediaStore.row(1, false, newest, "Camera", favorite = true)
    FakeMediaStore.rows += FakeMediaStore.row(2, true, newest - day, "Camera")
    FakeMediaStore.rows += FakeMediaStore.row(3, false, newest - 2 * day, "Screenshots", "Screenshot_1.png")
    FakeMediaStore.rows += FakeMediaStore.row(4, false, newest - 3 * day, "WhatsApp Images")
    FakeMediaStore.rows += FakeMediaStore.row(5, false, newest - 4 * day, "WhatsApp Images")
    FakeMediaStore.rows += FakeMediaStore.row(6, false, newest - 5 * day, "Camera")
    Robolectric.buildContentProvider(FakeMediaStore::class.java).create(ProviderInfo().apply { authority = MediaStore.AUTHORITY })
  }

  @Test fun iTreRegimi() {
    val photos = setOf(FluidMediaKind.Photo)
    val both = FluidMediaKind.PhotosAndVideos
    fun access(sdk: Int, kinds: Set<FluidMediaKind>, vararg granted: String) =
      fluidDeviceMediaAccess(sdk, kinds) { it in granted }
    assertEquals(FluidMediaAccess.Full, access(32, both, Manifest.permission.READ_EXTERNAL_STORAGE))
    assertEquals(FluidMediaAccess.NotAllowed(), access(32, both))
    assertEquals(FluidMediaAccess.Full, access(33, photos, Manifest.permission.READ_MEDIA_IMAGES))
    // Coi video servono tutti e due i permessi.
    assertEquals(FluidMediaAccess.NotAllowed(), access(33, both, Manifest.permission.READ_MEDIA_IMAGES))
    assertEquals(FluidMediaAccess.Full, access(34, both, Manifest.permission.READ_MEDIA_IMAGES, Manifest.permission.READ_MEDIA_VIDEO))
    // «Solo alcune foto»: il permesso parziale da solo (Android 14).
    assertEquals(FluidMediaAccess.Limited, access(34, both, "android.permission.READ_MEDIA_VISUAL_USER_SELECTED"))
    assertEquals(FluidMediaAccess.NotAllowed(), access(33, both, "android.permission.READ_MEDIA_VISUAL_USER_SELECTED"))
  }

  @Test fun senzaPermessoNonSiChiedeNienteAlMediaStore() = runBlocking {
    val source = FluidDeviceMediaSource(app)
    assertTrue(source.access is FluidMediaAccess.NotAllowed)
    assertTrue(source.page(FluidMediaQuery(), null, 10).items.isEmpty())
    assertTrue(source.albums().isEmpty())
    assertTrue(FakeMediaStore.asked.isEmpty())
  }

  @Test fun soloAlcuneElencaQuelloCheAndroidDa() = runBlocking {
    shadowOf(app).grantPermissions("android.permission.READ_MEDIA_VISUAL_USER_SELECTED")
    val source = FluidDeviceMediaSource(app)
    assertEquals(FluidMediaAccess.Limited, source.access)
    // Android da' da se' solo le foto concesse: qui il MediaStore finto ne ha due.
    FakeMediaStore.rows.retainAll { (it[0] as Long) in setOf(1L, 4L) }
    val page = source.page(FluidMediaQuery(), null, 10)
    assertEquals(listOf("1", "4"), page.items.map { it.id })
    // Concesse tutte: l'accesso si rilegge.
    shadowOf(app).grantPermissions(Manifest.permission.READ_MEDIA_IMAGES, Manifest.permission.READ_MEDIA_VIDEO)
    source.refresh()
    assertEquals(FluidMediaAccess.Full, source.access)
  }

  @Test fun paginaAlbumEConsegna() = runBlocking {
    shadowOf(app).grantPermissions(Manifest.permission.READ_MEDIA_IMAGES, Manifest.permission.READ_MEDIA_VIDEO)
    val source = FluidDeviceMediaSource(app)
    val first = source.page(FluidMediaQuery(), null, 4)
    assertEquals(listOf("1", "2", "3", "4"), first.items.map { it.id })
    assertEquals("4", first.next)
    val rest = source.page(FluidMediaQuery(), first.next, 4)
    assertEquals(listOf("5", "6"), rest.items.map { it.id })
    assertEquals(null, rest.next)
    // La domanda di Android 11+: limite (uno in piu', per sapere se c'e' altro) e spostamento.
    val args = FakeMediaStore.asked.last()!!
    assertEquals(5, args.getInt(android.content.ContentResolver.QUERY_ARG_LIMIT))
    assertEquals(4, args.getInt(android.content.ContentResolver.QUERY_ARG_OFFSET))
    val video = first.items[1]
    assertEquals(FluidMediaKind.Video, video.kind)
    assertEquals(65_000L, video.durationMillis)
    assertTrue(first.items[0].favorite)

    val albums = source.albums()
    assertEquals(listOf("Recenti", "Preferiti", "Video", "Screenshot", "Fotocamera", "WhatsApp Images"), albums.map { it.title })
    assertEquals(listOf(6, 1, 1, 1, 3, 2), albums.map { it.count })
    assertEquals(FluidMediaAlbumRole.Camera, albums[4].role)
    val camera = source.page(FluidMediaQuery(album = albums[4].id), null, 10)
    assertEquals(listOf("1", "2", "6"), camera.items.map { it.id })

    // La consegna: gli indirizzi del MediaStore, nell'ordine della scelta; uno sparito conta fra i saltati.
    FakeMediaStore.rows.removeAll { it[0] == 4L }
    val chosen = listOf(first.items[3], first.items[1], first.items[0])
    val delivery = source.deliver(chosen, FluidMediaRequest.attachments(10)) as FluidMediaDelivery.Ready
    assertEquals(listOf("2", "1"), delivery.media.map { it.item!!.id })
    assertEquals(1, delivery.skipped)
    assertEquals(FluidDeviceMediaSource.contentUri(FluidMediaKind.Video, 2), delivery.media[0].uri)
    assertTrue(delivery.media[0].uri.toString().startsWith("content://media/"))
  }

  @Test fun laRicercaPassaComeArgomento() {
    val (selection, args) = fluidDeviceSelection(FluidMediaKind.PhotosAndVideos, null, "mare 100%_x")
    assertFalse("il testo non entra nell'SQL", selection.contains("mare"))
    assertEquals(listOf("%mare%", "%mare%", "%100\\%\\_x%", "%100\\%\\_x%"), args.toList())
    val (bucket, bucketArgs) = fluidDeviceSelection(setOf(FluidMediaKind.Photo), "bucket:42", null)
    assertTrue(bucket.contains("bucket_id = ?"))
    assertEquals(listOf("42"), bucketArgs.toList())
    assertTrue(bucket.contains("IN (${MediaStore.Files.FileColumns.MEDIA_TYPE_IMAGE})"))
  }
}
