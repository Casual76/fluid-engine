package dev.antigravity.fluidengine.media

import android.content.Context
import android.content.pm.ProviderInfo
import android.graphics.BitmapFactory
import android.net.Uri
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** La sorgente finta dietro il provider, come la mettera' MyCloud Gallery in F12. */
class TestGalleryProvider : FluidMediaSourceProvider() {
  override fun createSource(context: Context): FluidMediaSource = Switched()

  companion object {
    var shared: FluidFakeMediaSource = FluidFakeMediaSource.sample()
    var allowCaller: Boolean = true

    /** R3: la sorgente dietro il provider lancia per pagine e album (il NAS spento a meta'). */
    @Volatile var failing: Boolean = false
  }

  /** [shared] com'e' adesso, che lancia quando [failing]. */
  private class Switched : FluidMediaSource {
    private val inner: FluidFakeMediaSource get() = shared
    override val id: String get() = inner.id
    override val label: String get() = inner.label
    override val capabilities: FluidMediaSourceCapabilities get() = inner.capabilities
    override val access: FluidMediaAccess get() = inner.access
    override suspend fun refresh() = inner.refresh()
    override suspend fun albums(): List<FluidMediaAlbum> {
      if (failing) throw java.io.IOException("Il NAS non risponde")
      return inner.albums()
    }
    override suspend fun page(query: FluidMediaQuery, cursor: String?, limit: Int): FluidMediaPage {
      if (failing) throw java.io.IOException("Il NAS non risponde")
      return inner.page(query, cursor, limit)
    }
    override suspend fun thumbnail(item: FluidMediaItem, sizePx: Int) = inner.thumbnail(item, sizePx)
    override suspend fun deliver(items: List<FluidMediaItem>, request: FluidMediaRequest, onProgress: (FluidMediaProgress) -> Unit) =
      inner.deliver(items, request, onProgress)
  }

  override fun isCallerAllowed(callingPackage: String?): Boolean = allowCaller && super.isCallerAllowed(callingPackage)
}

/**
 * F8.14 · Il contratto delle sorgenti fra app, **dal vero**: un `ContentProvider` registrato in Robolectric che serve una
 * sorgente finta (la parte che MyCloud Gallery fara' in F12) e il lettore dell'engine che lo usa attraverso il
 * `ContentResolver`, con i `Bundle` di Android. Album, pagine, la ricerca per significato, le miniature su un tubo, la
 * consegna nell'ordine della scelta, il blocco, il NAS spento e chi non puo' chiedere.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34])
class FluidMediaSourceContractTest {
  private val context: Context = ApplicationProvider.getApplicationContext()
  private val authority = "dev.antigravity.fluidengine.media.test"

  @Before
  fun register() {
    TestGalleryProvider.shared = FluidFakeMediaSource.sample(count = 50)
    TestGalleryProvider.allowCaller = true
    TestGalleryProvider.failing = false
    val info = ProviderInfo().apply {
      this.authority = this@FluidMediaSourceContractTest.authority
      packageName = context.packageName
      exported = true
    }
    Robolectric.buildContentProvider(TestGalleryProvider::class.java).create(info)
  }

  @After
  fun clean() {
    TestGalleryProvider.allowCaller = true
    TestGalleryProvider.failing = false
  }

  private fun connect(): FluidRemoteMediaSource = runBlocking { FluidMediaSources.connect(context, authority) }
    ?: error("Il provider non ha risposto")

  @Test fun chiEECosaSaFare() {
    val remote = connect()
    assertEquals("mycloud", remote.id)
    assertEquals("MyCloud Gallery", remote.label)
    assertTrue(remote.capabilities.search)
    assertTrue(remote.capabilities.semanticSearch)
    assertTrue(remote.capabilities.backupOnly)
    assertEquals(FluidMediaAccess.Full, remote.access)
  }

  @Test fun albumELibreria() = runBlocking {
    val remote = connect()
    val albums = remote.albums()
    assertEquals(listOf("all", "backup-only", "vacanze", "famiglia", "pippo"), albums.map { it.id })
    assertEquals(FluidMediaAlbumRole.BackupOnly, albums[1].role)
    assertEquals(50, albums[0].count)
    assertNotNull(albums[0].cover)
    assertTrue(albums[1].cover!!.backupOnly)
    // Le pagine: 50 elementi, 20 per pagina, dal piu' recente; il segno di pagina e' opaco.
    val first = remote.page(FluidMediaQuery(), null, 20)
    assertEquals(20, first.items.size)
    val second = remote.page(FluidMediaQuery(), first.next, 20)
    val third = remote.page(FluidMediaQuery(), second.next, 20)
    assertEquals(10, third.items.size)
    assertNull(third.next)
    val all = first.items + second.items + third.items
    assertEquals(50, all.map { it.id }.toSet().size)
    assertTrue(all.zipWithNext().all { (a, b) -> (a.takenAtMillis ?: 0) >= (b.takenAtMillis ?: 0) })
    // Ogni campo passa il confine: tipo, durata, nuvola, preferito, descrizione.
    val video = all.first { it.kind == FluidMediaKind.Video }
    assertNotNull(video.durationMillis)
    assertEquals("mycloud", video.source)
    assertTrue(all.any { it.backupOnly } && all.any { it.favorite } && all.all { it.description != null })
    // Un album: solo i suoi.
    val backup = remote.page(FluidMediaQuery(album = "backup-only"), null, 100)
    assertTrue(backup.items.isNotEmpty() && backup.items.all { it.backupOnly })
    // Solo locali: niente di cio' che sta solo nel backup.
    val local = remote.page(FluidMediaQuery(localOnly = true), null, 100)
    assertTrue(local.items.none { it.backupOnly })
  }

  @Test fun laRicercaPerSignificatoPassaIlConfine() = runBlocking {
    val remote = connect()
    val page = remote.page(FluidMediaQuery(text = "mare d'estate"), null, 100)
    assertEquals(listOf("Mare", "Estate"), page.interpretation)
    assertTrue(page.items.isNotEmpty())
    val fake = TestGalleryProvider.shared
    val tagged = fake.entries.filter { e -> e.tags.any { it == "mare" || it == "spiaggia" } }.map { it.item.id }.toSet()
    assertTrue(page.items.all { it.id in tagged })
    assertEquals("mare d'estate", fake.queries.last().text)
    // Solo video.
    val videos = remote.page(FluidMediaQuery(text = "montagna", kinds = setOf(FluidMediaKind.Video)), null, 100)
    assertTrue(videos.items.all { it.kind == FluidMediaKind.Video })
  }

  @Test fun laMiniaturaArrivaSulTubo() = runBlocking {
    val remote = connect()
    val item = remote.page(FluidMediaQuery(), null, 1).items.single()
    val bitmap = remote.thumbnail(item, 128)
    assertNotNull("la miniatura passa da openTypedAssetFile", bitmap)
    assertEquals(128, bitmap!!.width)
    val uri = FluidMediaSourceContract.thumbnailUri(authority, item)
    assertEquals("thumbnail", uri.pathSegments.first())
    assertEquals(item.id, uri.getQueryParameter("id"))
  }

  @Test fun laConsegnaTieneLOrdineDellaScelta() = runBlocking {
    val remote = connect()
    val items = remote.page(FluidMediaQuery(localOnly = true), null, 10).items
    val chosen = listOf(items[4], items[0], items[7])
    val delivery = remote.deliver(chosen, FluidMediaRequest.attachments(10))
    delivery as FluidMediaDelivery.Ready
    assertEquals(chosen.map { it.id }, delivery.media.map { it.item!!.id })
    assertEquals(chosen.map { it.id }, TestGalleryProvider.shared.deliveries.last())
    assertTrue(delivery.media.all { it.uri.scheme == "content" && it.source == "mycloud" })
    assertEquals(items[4].name, delivery.media[0].info.name)
  }

  @Test fun bloccataChiedeLoSblocco() = runBlocking {
    TestGalleryProvider.shared.lock()
    val remote = connect()
    assertTrue(remote.access is FluidMediaAccess.Locked)
    // Niente si elenca finche' e' chiusa.
    assertTrue(remote.albums().isEmpty())
    assertTrue(remote.page(FluidMediaQuery(), null, 10).items.isEmpty())
    val item = FluidMediaItem("mycloud", "m000")
    assertTrue(remote.deliver(listOf(item), FluidMediaRequest()) is FluidMediaDelivery.NeedsUnlock)
    // Sbloccata: si rilegge.
    TestGalleryProvider.shared.access = FluidMediaAccess.Full
    remote.refresh()
    assertEquals(FluidMediaAccess.Full, remote.access)
  }

  @Test fun ilNasSpentoSiDicePrimaDiConsegnare() = runBlocking {
    val remote = connect()
    val items = remote.page(FluidMediaQuery(album = "backup-only"), null, 3).items
    TestGalleryProvider.shared.backupReachable = false
    val delivery = remote.deliver(items, FluidMediaRequest.attachments(10))
    delivery as FluidMediaDelivery.Refused
    assertEquals(FluidMediaRefusal.Unreachable, delivery.reason)
    assertTrue(TestGalleryProvider.shared.deliveries.isEmpty())
  }

  @Test fun unaPaginaOGliAlbumCheLaSorgenteNonDaNonSembranoVuoti() = runBlocking {
    // R3 (difetto 2b): il provider risponde `error` (la scadenza o l'errore della sorgente dietro), e chi legge prima lo
    // ignorava: pagina vuota e finita, accesso che restava pieno, nessun «Riprova».
    val remote = connect()
    assertEquals(5, remote.albums().size)
    TestGalleryProvider.failing = true
    val page = runCatching { remote.page(FluidMediaQuery(), null, 20) }
    assertTrue("la pagina che non arriva e' un errore, non una pagina vuota: $page", page.exceptionOrNull() is java.io.IOException)
    val albums = runCatching { remote.albums() }
    assertTrue("gli album che non arrivano sono un errore, non zero album: $albums", albums.exceptionOrNull() is java.io.IOException)
    assertEquals("il provider ha risposto: la sorgente c'e'", FluidMediaAccess.Full, remote.access)
    // Il NAS torna: si riprova e arriva.
    TestGalleryProvider.failing = false
    assertEquals(20, remote.page(FluidMediaQuery(), null, 20).items.size)
    assertEquals(5, remote.albums().size)
  }

  @Test fun ilTettoDellaConsegna() = runBlocking {
    val remote = connect()
    val items = remote.page(FluidMediaQuery(localOnly = true), null, 5).items
    val delivery = remote.deliver(items, FluidMediaRequest.attachments(3))
    delivery as FluidMediaDelivery.Refused
    assertEquals(FluidMediaRefusal.tooMany(3), delivery.reason)
  }

  @Test fun chiNonPuoChiedereNonVedeNiente() = runBlocking {
    TestGalleryProvider.allowCaller = false
    assertNull("la sorgente non si collega", FluidMediaSources.connect(context, authority))
    TestGalleryProvider.allowCaller = true
    val remote = connect()
    TestGalleryProvider.allowCaller = false
    assertTrue(remote.page(FluidMediaQuery(), null, 10).items.isEmpty())
    assertEquals(FluidMediaAccess.Unavailable(FluidMediaSourceContract.UNAVAILABLE_GONE), remote.access)
  }

  @Test fun leTraduzioniNonPerdonoNiente() {
    val items = listOf(
      FluidMediaItem("s", "a", FluidMediaKind.Video, "video/mp4", 1920, 1080, 42_000L, 1_700_000_000_000L, 5L, "VID.mp4", backupOnly = true, favorite = true, description = "Mare"),
      FluidMediaItem("s", "b"),
      FluidMediaItem("s", "c", FluidMediaKind.of("live"), name = null),
    )
    assertEquals(items, FluidMediaSourceContract.readItems(FluidMediaSourceContract.encodeItems(items), "s"))
    val albums = listOf(
      FluidMediaAlbum("s", "1", "Uno", 3, items[0], FluidMediaAlbumRole.Favorites),
      FluidMediaAlbum("s", "2", "Due", null, null, FluidMediaAlbumRole.User),
    )
    assertEquals(albums, FluidMediaSourceContract.readAlbums(FluidMediaSourceContract.encodeAlbums(albums), "s"))
    val query = FluidMediaQuery(album = "x", text = "neve", kinds = setOf(FluidMediaKind.Photo), localOnly = true)
    assertEquals(Triple(query, "7", 30), FluidMediaSourceContract.readQuery(FluidMediaSourceContract.encodeQuery(query, "7", 30)))
    for (access in listOf(
      FluidMediaAccess.Full, FluidMediaAccess.Limited, FluidMediaAccess.NotAllowed(false), FluidMediaAccess.NotAllowed(true),
      FluidMediaAccess.Locked(null), FluidMediaAccess.Unavailable("stopped"),
    )) {
      val b = android.os.Bundle().also { FluidMediaSourceContract.putAccess(it, access) }
      assertEquals(access, FluidMediaSourceContract.readAccess(b))
    }
    val refused = FluidMediaSourceContract.readDelivery(
      FluidMediaSourceContract.encodeDelivery(FluidMediaDelivery.Refused(FluidMediaRefusal.tooMany(4), "a")), "s",
    ) as FluidMediaDelivery.Refused
    assertEquals(FluidMediaRefusal.tooMany(4), refused.reason)
    assertEquals("a", refused.culprit)
    // Un indirizzo che non e' content:// non esce mai.
    val ready = FluidMediaSourceContract.readDelivery(
      FluidMediaSourceContract.encodeDelivery(
        FluidMediaDelivery.Ready(
          listOf(
            FluidPickedMedia(Uri.parse("file:///sdcard/x.jpg"), dev.antigravity.fluidengine.ui.fluid.viewer.FluidFileInfo("x.jpg"), source = "s"),
            FluidPickedMedia(Uri.parse("content://a/1"), dev.antigravity.fluidengine.ui.fluid.viewer.FluidFileInfo("y.jpg"), source = "s"),
          ),
        ),
      ),
      "s",
    ) as FluidMediaDelivery.Ready
    assertEquals(listOf("content://a/1"), ready.media.map { it.uri.toString() })
    assertEquals(1, ready.skipped)
    // Senza risposta: la sorgente non c'e'.
    assertEquals(FluidMediaAccess.Unavailable(FluidMediaSourceContract.UNAVAILABLE_GONE), FluidMediaSourceContract.readAccess(null))
  }

  @Test fun laMiniaturaSiDecodifica() {
    // Il formato sul tubo e' JPEG: lo decodifica qualunque lettore.
    val fake = TestGalleryProvider.shared
    val bitmap = runBlocking { fake.thumbnail(fake.entries.first().item, 64) }!!
    val out = java.io.ByteArrayOutputStream()
    bitmap.compress(android.graphics.Bitmap.CompressFormat.JPEG, 85, out)
    assertNotNull(BitmapFactory.decodeByteArray(out.toByteArray(), 0, out.size()))
  }
}
