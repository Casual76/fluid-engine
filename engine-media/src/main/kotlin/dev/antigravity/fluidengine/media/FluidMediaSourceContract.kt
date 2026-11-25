package dev.antigravity.fluidengine.media

import android.app.PendingIntent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import dev.antigravity.fluidengine.ui.fluid.viewer.FluidFileInfo

/**
 * Il contratto fra app delle sorgenti di foto e video (F8.14), **versione 1**: come un'app (in F12 MyCloud Gallery) offre
 * la sua Libreria, la sua ricerca e le foto che stanno solo nel suo backup al selettore delle altre app Pampa, senza che
 * queste vedano altro.
 *
 * **Chi serve** dichiara nel manifest un provider che estende [FluidMediaSourceProvider]:
 *
 * ```xml
 * <provider android:name=".media.GalleryMediaSource"
 *     android:authorities="dev.pampa.mycloudgallery.fluidmedia"
 *     android:exported="true">
 *   <intent-filter><action android:name="dev.antigravity.fluidengine.media.SOURCE" /></intent-filter>
 * </provider>
 * ```
 *
 * **Chi legge** lo trova con [FluidMediaSources.discover] (il modulo dichiara gia' la visibilita' dell'azione, `<queries>`)
 * e lo usa come una [FluidMediaSource] qualunque ([FluidRemoteMediaSource]).
 *
 * **Cosa passa** (tutto in `ContentProvider.call`, con `Bundle` di tipi di Android, e le miniature in
 * `openTypedAssetFile`):
 *
 * | metodo | cosa chiede | cosa risponde |
 * |---|---|---|
 * | [METHOD_INFO] | — | versione, id, nome, cosa sa fare, l'accesso di adesso (con lo sblocco) |
 * | [METHOD_ALBUMS] | — | gli album, con la copertina |
 * | [METHOD_PAGE] | album, testo, tipi, solo locali, segno di pagina, quanti | una pagina di elementi, il segno dopo, come ha capito la ricerca |
 * | [METHOD_DELIVER] | gli id nell'ordine della scelta, i tipi ammessi, il tetto | gli indirizzi (col permesso di lettura dato a chi chiede), o il rifiuto col motivo, o lo sblocco |
 * | miniatura | `content://<autorita'>/thumbnail?id=…&kind=…`, lato in `ContentResolver.EXTRA_SIZE` | un'immagine JPEG |
 *
 * **Chi puo' chiedere.** Di serie solo le app firmate con la stessa chiave di chi serve (le app Pampa:
 * [FluidMediaSourceProvider.isCallerAllowed]); ogni altra chiamata riceve `SecurityException`. Chi legge, di serie, si fida
 * solo dei provider firmati come lui ([FluidMediaSourceTrust.SameSignature]).
 *
 * **Le regole di chi serve** sono quelle di [FluidMediaSource]: la vista di adesso decide, id opachi, scadenze, l'ordine
 * della scelta. MyCloud Gallery le ha gia' nel suo selettore (`PickerDelivery.kt`: i nascosti, il blocco dell'app, i tetti
 * dei solo-NAS, il NAS irraggiungibile detto prima di creare un id, «Rimuovi la posizione»), e in F12 le mette dietro
 * questo contratto.
 *
 * Una versione nuova aggiunge chiavi, non le cambia: chi legge ignora le chiavi che non conosce, e una risposta senza una
 * chiave vale come il suo valore di serie.
 */
object FluidMediaSourceContract {
  /** La versione di questo contratto. */
  const val VERSION: Int = 1

  /** L'azione dell'`intent-filter` del provider: e' cosi' che chi legge trova le sorgenti. */
  const val ACTION_SOURCE: String = "dev.antigravity.fluidengine.media.SOURCE"

  const val METHOD_INFO: String = "fluid.media.info"
  const val METHOD_ALBUMS: String = "fluid.media.albums"
  const val METHOD_PAGE: String = "fluid.media.page"
  const val METHOD_DELIVER: String = "fluid.media.deliver"

  /** Il percorso delle miniature: `content://<autorita'>/thumbnail?id=<id>&kind=<tipo>`. */
  const val PATH_THUMBNAIL: String = "thumbnail"

  /** Il tetto di una pagina: oltre, chi serve taglia (un `Bundle` di trecento elementi sta ben sotto il limite del binder). */
  const val MAX_PAGE: Int = 300

  /** Il tetto di una consegna sola. */
  const val MAX_DELIVERY: Int = 100

  // --- I motivi per cui una sorgente non c'e' ([FluidMediaAccess.Unavailable]) ---------------------------------------

  /** L'app che serve non e' pronta (si sta avviando, non e' configurata). */
  const val UNAVAILABLE_NOT_READY: String = "not-ready"

  /** L'app che serve e' ferma (una versione ritirata, un'impostazione che la spegne). */
  const val UNAVAILABLE_STOPPED: String = "stopped"

  /** Il provider non risponde o non c'e' piu' (l'app e' stata tolta). */
  const val UNAVAILABLE_GONE: String = "gone"

  /** Il provider parla una versione del contratto che chi legge non conosce. */
  const val UNAVAILABLE_VERSION: String = "version"

  // --- Le chiavi ----------------------------------------------------------------------------------------------------

  internal const val KEY_VERSION = "version"
  internal const val KEY_ID = "id"
  internal const val KEY_LABEL = "label"
  internal const val KEY_SEARCH = "search"
  internal const val KEY_SEMANTIC = "semantic"
  internal const val KEY_ALBUMS = "albums"
  internal const val KEY_BACKUP_ONLY = "backupOnly"
  internal const val KEY_MAX_DELIVERY = "maxDelivery"
  internal const val KEY_ACCESS = "access"
  internal const val KEY_REASON = "reason"
  internal const val KEY_UNLOCK = "unlock"

  internal const val KEY_ALBUM = "album"
  internal const val KEY_TEXT = "text"
  internal const val KEY_KINDS = "kinds"
  internal const val KEY_LOCAL_ONLY = "localOnly"
  internal const val KEY_CURSOR = "cursor"
  internal const val KEY_LIMIT = "limit"
  internal const val KEY_NEXT = "next"
  internal const val KEY_TOTAL = "total"
  internal const val KEY_INTERPRETATION = "interpretation"
  internal const val KEY_ITEMS = "items"

  internal const val KEY_IDS = "ids"
  internal const val KEY_MIME_TYPES = "mimeTypes"
  internal const val KEY_STATUS = "status"
  internal const val KEY_URIS = "uris"
  internal const val KEY_SKIPPED = "skipped"
  internal const val KEY_REFUSAL = "refusal"
  internal const val KEY_CULPRIT = "culprit"
  internal const val KEY_ERROR = "error"

  internal const val ACCESS_FULL = "full"
  internal const val ACCESS_LIMITED = "limited"
  internal const val ACCESS_NOT_ALLOWED = "not-allowed"
  internal const val ACCESS_NOT_ALLOWED_FOREVER = "not-allowed-forever"
  internal const val ACCESS_LOCKED = "locked"
  internal const val ACCESS_UNAVAILABLE = "unavailable"

  internal const val STATUS_READY = "ready"
  internal const val STATUS_REFUSED = "refused"
  internal const val STATUS_UNLOCK = "unlock"

  internal const val QUERY_ID = "id"
  internal const val QUERY_KIND = "kind"

  // Gli elementi in colonne: un array per campo, la stessa posizione per lo stesso elemento.
  private const val C_ID = "c.id"
  private const val C_KIND = "c.kind"
  private const val C_MIME = "c.mime"
  private const val C_WIDTH = "c.width"
  private const val C_HEIGHT = "c.height"
  private const val C_DURATION = "c.duration"
  private const val C_TAKEN = "c.taken"
  private const val C_SIZE = "c.size"
  private const val C_NAME = "c.name"
  private const val C_FLAGS = "c.flags"
  private const val C_DESCRIPTION = "c.description"
  private const val FLAG_BACKUP_ONLY = 1
  private const val FLAG_FAVORITE = 2

  private const val A_ID = "a.id"
  private const val A_TITLE = "a.title"
  private const val A_COUNT = "a.count"
  private const val A_ROLE = "a.role"
  private const val A_COVER = "a.cover"
  private const val A_COVERS = "a.covers"

  // --- Le traduzioni fra oggetti e Bundle (le usano il provider e chi legge; le prove le controllano) -------------------

  internal fun encodeInfo(source: FluidMediaSource): Bundle = Bundle().apply {
    putInt(KEY_VERSION, VERSION)
    putString(KEY_ID, source.id)
    putString(KEY_LABEL, source.label)
    val c = source.capabilities
    putBoolean(KEY_SEARCH, c.search)
    putBoolean(KEY_SEMANTIC, c.semanticSearch)
    putBoolean(KEY_ALBUMS, c.albums)
    putBoolean(KEY_BACKUP_ONLY, c.backupOnly)
    putInt(KEY_MAX_DELIVERY, c.maxDelivery ?: -1)
    putAccess(this, source.access)
  }

  internal fun putAccess(bundle: Bundle, access: FluidMediaAccess) {
    when (access) {
      FluidMediaAccess.Full -> bundle.putString(KEY_ACCESS, ACCESS_FULL)
      FluidMediaAccess.Limited -> bundle.putString(KEY_ACCESS, ACCESS_LIMITED)
      is FluidMediaAccess.NotAllowed ->
        bundle.putString(KEY_ACCESS, if (access.forever) ACCESS_NOT_ALLOWED_FOREVER else ACCESS_NOT_ALLOWED)
      is FluidMediaAccess.Locked -> {
        bundle.putString(KEY_ACCESS, ACCESS_LOCKED)
        bundle.putParcelable(KEY_UNLOCK, access.unlock)
      }
      is FluidMediaAccess.Unavailable -> {
        bundle.putString(KEY_ACCESS, ACCESS_UNAVAILABLE)
        bundle.putString(KEY_REASON, access.reason)
      }
    }
  }

  internal fun readAccess(bundle: Bundle?): FluidMediaAccess = when (bundle?.getString(KEY_ACCESS)) {
    ACCESS_FULL -> FluidMediaAccess.Full
    ACCESS_LIMITED -> FluidMediaAccess.Limited
    ACCESS_NOT_ALLOWED -> FluidMediaAccess.NotAllowed(forever = false)
    ACCESS_NOT_ALLOWED_FOREVER -> FluidMediaAccess.NotAllowed(forever = true)
    ACCESS_LOCKED -> FluidMediaAccess.Locked(bundle.pendingIntent(KEY_UNLOCK))
    ACCESS_UNAVAILABLE -> FluidMediaAccess.Unavailable(bundle.getString(KEY_REASON) ?: UNAVAILABLE_NOT_READY)
    else -> FluidMediaAccess.Unavailable(if (bundle == null) UNAVAILABLE_GONE else UNAVAILABLE_NOT_READY)
  }

  internal fun readCapabilities(bundle: Bundle): FluidMediaSourceCapabilities = FluidMediaSourceCapabilities(
    search = bundle.getBoolean(KEY_SEARCH, false),
    semanticSearch = bundle.getBoolean(KEY_SEMANTIC, false),
    albums = bundle.getBoolean(KEY_ALBUMS, true),
    backupOnly = bundle.getBoolean(KEY_BACKUP_ONLY, false),
    maxDelivery = bundle.getInt(KEY_MAX_DELIVERY, -1).takeIf { it > 0 },
  )

  internal fun encodeQuery(query: FluidMediaQuery, cursor: String?, limit: Int): Bundle = Bundle().apply {
    putString(KEY_ALBUM, query.album)
    putString(KEY_TEXT, query.text)
    putStringArray(KEY_KINDS, query.kinds.map { it.id }.toTypedArray())
    putBoolean(KEY_LOCAL_ONLY, query.localOnly)
    putString(KEY_CURSOR, cursor)
    putInt(KEY_LIMIT, limit.coerceIn(1, MAX_PAGE))
  }

  internal fun readQuery(bundle: Bundle?): Triple<FluidMediaQuery, String?, Int> {
    val b = bundle ?: Bundle.EMPTY
    val kinds = b.getStringArray(KEY_KINDS)?.map { FluidMediaKind.of(it) }?.toSet()?.takeIf { it.isNotEmpty() }
      ?: FluidMediaKind.PhotosAndVideos
    val query = FluidMediaQuery(
      album = b.getString(KEY_ALBUM),
      text = b.getString(KEY_TEXT)?.take(MAX_TEXT),
      kinds = kinds,
      localOnly = b.getBoolean(KEY_LOCAL_ONLY, false),
    )
    return Triple(query, b.getString(KEY_CURSOR), b.getInt(KEY_LIMIT, 60).coerceIn(1, MAX_PAGE))
  }

  /** Il testo di una ricerca che passa fra app: al piu' 500 caratteri. */
  private const val MAX_TEXT = 500

  internal fun encodePage(page: FluidMediaPage): Bundle = Bundle().apply {
    putBundle(KEY_ITEMS, encodeItems(page.items.take(MAX_PAGE)))
    putString(KEY_NEXT, page.next)
    putInt(KEY_TOTAL, page.total ?: -1)
    putStringArray(KEY_INTERPRETATION, page.interpretation.toTypedArray())
  }

  internal fun readPage(bundle: Bundle?, source: String): FluidMediaPage {
    if (bundle == null) return FluidMediaPage(emptyList())
    return FluidMediaPage(
      items = readItems(bundle.getBundle(KEY_ITEMS), source),
      next = bundle.getString(KEY_NEXT),
      total = bundle.getInt(KEY_TOTAL, -1).takeIf { it >= 0 },
      interpretation = bundle.getStringArray(KEY_INTERPRETATION)?.toList().orEmpty(),
    )
  }

  internal fun encodeAlbums(albums: List<FluidMediaAlbum>): Bundle = Bundle().apply {
    putStringArray(A_ID, albums.map { it.id }.toTypedArray())
    putStringArray(A_TITLE, albums.map { it.title }.toTypedArray())
    putIntArray(A_COUNT, albums.map { it.count ?: -1 }.toIntArray())
    putStringArray(A_ROLE, albums.map { it.role.id }.toTypedArray())
    val covers = albums.mapNotNull { it.cover }
    var next = 0
    putIntArray(A_COVER, albums.map { if (it.cover != null) next++ else -1 }.toIntArray())
    putBundle(A_COVERS, encodeItems(covers))
  }

  internal fun readAlbums(bundle: Bundle?, source: String): List<FluidMediaAlbum> {
    if (bundle == null) return emptyList()
    val ids = bundle.getStringArray(A_ID) ?: return emptyList()
    val titles = bundle.getStringArray(A_TITLE) ?: return emptyList()
    val counts = bundle.getIntArray(A_COUNT)
    val roles = bundle.getStringArray(A_ROLE)
    val coverAt = bundle.getIntArray(A_COVER)
    val covers = readItems(bundle.getBundle(A_COVERS), source)
    return ids.indices.mapNotNull { i ->
      val title = titles.getOrNull(i) ?: return@mapNotNull null
      FluidMediaAlbum(
        source = source,
        id = ids[i],
        title = title,
        count = counts?.getOrNull(i)?.takeIf { it >= 0 },
        cover = coverAt?.getOrNull(i)?.let { covers.getOrNull(it) },
        role = roles?.getOrNull(i)?.let { FluidMediaAlbumRole.of(it) } ?: FluidMediaAlbumRole.User,
      )
    }
  }

  internal fun encodeItems(items: List<FluidMediaItem>): Bundle = Bundle().apply {
    putStringArray(C_ID, items.map { it.id }.toTypedArray())
    putStringArray(C_KIND, items.map { it.kind.id }.toTypedArray())
    putStringArray(C_MIME, items.map { it.mimeType }.toTypedArray())
    putIntArray(C_WIDTH, items.map { it.width ?: -1 }.toIntArray())
    putIntArray(C_HEIGHT, items.map { it.height ?: -1 }.toIntArray())
    putLongArray(C_DURATION, items.map { it.durationMillis ?: -1L }.toLongArray())
    putLongArray(C_TAKEN, items.map { it.takenAtMillis ?: Long.MIN_VALUE }.toLongArray())
    putLongArray(C_SIZE, items.map { it.sizeBytes ?: -1L }.toLongArray())
    putStringArray(C_NAME, items.map { it.name }.toTypedArray())
    putIntArray(
      C_FLAGS,
      items.map { (if (it.backupOnly) FLAG_BACKUP_ONLY else 0) or (if (it.favorite) FLAG_FAVORITE else 0) }.toIntArray(),
    )
    putStringArray(C_DESCRIPTION, items.map { it.description }.toTypedArray())
  }

  internal fun readItems(bundle: Bundle?, source: String): List<FluidMediaItem> {
    if (bundle == null) return emptyList()
    val ids = bundle.getStringArray(C_ID) ?: return emptyList()
    val kinds = bundle.getStringArray(C_KIND)
    val mimes = bundle.getStringArray(C_MIME)
    val widths = bundle.getIntArray(C_WIDTH)
    val heights = bundle.getIntArray(C_HEIGHT)
    val durations = bundle.getLongArray(C_DURATION)
    val taken = bundle.getLongArray(C_TAKEN)
    val sizes = bundle.getLongArray(C_SIZE)
    val names = bundle.getStringArray(C_NAME)
    val flags = bundle.getIntArray(C_FLAGS)
    val descriptions = bundle.getStringArray(C_DESCRIPTION)
    return ids.indices.map { i ->
      val f = flags?.getOrNull(i) ?: 0
      FluidMediaItem(
        source = source,
        id = ids[i],
        kind = kinds?.getOrNull(i)?.let { FluidMediaKind.of(it) } ?: FluidMediaKind.from(mimes?.getOrNull(i)),
        mimeType = mimes?.getOrNull(i),
        width = widths?.getOrNull(i)?.takeIf { it > 0 },
        height = heights?.getOrNull(i)?.takeIf { it > 0 },
        durationMillis = durations?.getOrNull(i)?.takeIf { it >= 0 },
        takenAtMillis = taken?.getOrNull(i)?.takeIf { it != Long.MIN_VALUE },
        sizeBytes = sizes?.getOrNull(i)?.takeIf { it >= 0 },
        name = names?.getOrNull(i),
        backupOnly = f and FLAG_BACKUP_ONLY != 0,
        favorite = f and FLAG_FAVORITE != 0,
        description = descriptions?.getOrNull(i),
      )
    }
  }

  internal fun encodeDeliverRequest(items: List<FluidMediaItem>, request: FluidMediaRequest): Bundle = Bundle().apply {
    putStringArray(KEY_IDS, items.map { it.id }.toTypedArray())
    putBundle(KEY_ITEMS, encodeItems(items))
    putStringArray(KEY_KINDS, request.kinds.map { it.id }.toTypedArray())
    putStringArray(KEY_MIME_TYPES, request.mimeTypes?.toTypedArray())
    putInt(KEY_LIMIT, request.limit)
    putBoolean(KEY_LOCAL_ONLY, request.localOnly)
  }

  internal fun readDeliverRequest(bundle: Bundle?, source: String): Pair<List<FluidMediaItem>, FluidMediaRequest> {
    val b = bundle ?: Bundle.EMPTY
    val items = readItems(b.getBundle(KEY_ITEMS), source).take(MAX_DELIVERY + 1)
    val kinds = b.getStringArray(KEY_KINDS)?.map { FluidMediaKind.of(it) }?.toSet()?.takeIf { it.isNotEmpty() }
      ?: FluidMediaKind.PhotosAndVideos
    val request = FluidMediaRequest(
      kinds = kinds,
      limit = b.getInt(KEY_LIMIT, MAX_DELIVERY).coerceIn(1, MAX_DELIVERY),
      mimeTypes = b.getStringArray(KEY_MIME_TYPES)?.toList(),
      localOnly = b.getBoolean(KEY_LOCAL_ONLY, false),
    )
    return items to request
  }

  internal fun encodeDelivery(delivery: FluidMediaDelivery): Bundle = Bundle().apply {
    when (delivery) {
      is FluidMediaDelivery.Ready -> {
        putString(KEY_STATUS, STATUS_READY)
        putParcelableArrayList(KEY_URIS, ArrayList(delivery.media.map { it.uri }))
        val items = delivery.media.map { picked ->
          picked.item ?: FluidMediaItem(source = picked.source, id = picked.uri.toString(), kind = picked.kind)
        }.mapIndexed { i, item ->
          val info = delivery.media[i].info
          item.copy(
            mimeType = info.mimeType ?: item.mimeType,
            name = info.name,
            sizeBytes = info.sizeBytes ?: item.sizeBytes,
            width = delivery.media[i].width ?: item.width,
            height = delivery.media[i].height ?: item.height,
            durationMillis = delivery.media[i].durationMillis ?: item.durationMillis,
          )
        }
        putBundle(KEY_ITEMS, encodeItems(items))
        putInt(KEY_SKIPPED, delivery.skipped)
      }
      is FluidMediaDelivery.Refused -> {
        putString(KEY_STATUS, STATUS_REFUSED)
        putString(KEY_REFUSAL, delivery.reason.code)
        putInt(KEY_LIMIT, delivery.reason.limit ?: -1)
        putString(KEY_CULPRIT, delivery.culprit)
      }
      is FluidMediaDelivery.NeedsUnlock -> {
        putString(KEY_STATUS, STATUS_UNLOCK)
        putParcelable(KEY_UNLOCK, delivery.unlock)
      }
    }
  }

  internal fun readDelivery(bundle: Bundle?, source: String): FluidMediaDelivery {
    val b = bundle ?: return FluidMediaDelivery.Refused(FluidMediaRefusal.Failed)
    return when (b.getString(KEY_STATUS)) {
      STATUS_READY -> {
        val uris = b.uriList(KEY_URIS)
        val items = readItems(b.getBundle(KEY_ITEMS), source)
        val media = uris.mapIndexedNotNull { i, uri ->
          if (uri.scheme != "content") return@mapIndexedNotNull null
          val item = items.getOrNull(i)
          FluidPickedMedia(
            uri = uri,
            info = FluidFileInfo(
              name = item?.name ?: uri.lastPathSegment.orEmpty(),
              mimeType = item?.mimeType,
              sizeBytes = item?.sizeBytes,
              modifiedAtMillis = item?.takenAtMillis,
            ),
            kind = item?.kind ?: FluidMediaKind.Photo,
            width = item?.width,
            height = item?.height,
            durationMillis = item?.durationMillis,
            source = source,
            item = item,
          )
        }
        FluidMediaDelivery.Ready(media, skipped = b.getInt(KEY_SKIPPED, 0) + (uris.size - media.size))
      }
      STATUS_UNLOCK -> FluidMediaDelivery.NeedsUnlock(b.pendingIntent(KEY_UNLOCK))
      STATUS_REFUSED -> FluidMediaDelivery.Refused(
        FluidMediaRefusal.of(b.getString(KEY_REFUSAL) ?: FluidMediaRefusal.Failed.code, b.getInt(KEY_LIMIT, -1).takeIf { it >= 0 }),
        b.getString(KEY_CULPRIT),
      )
      else -> FluidMediaDelivery.Refused(FluidMediaRefusal.Failed)
    }
  }

  /** Il percorso della miniatura di [item] presso l'autorita' [authority]. */
  fun thumbnailUri(authority: String, item: FluidMediaItem): Uri = Uri.Builder()
    .scheme("content")
    .authority(authority)
    .appendPath(PATH_THUMBNAIL)
    .appendQueryParameter(QUERY_ID, item.id)
    .appendQueryParameter(QUERY_KIND, item.kind.id)
    .build()

  @Suppress("DEPRECATION")
  private fun Bundle.pendingIntent(key: String): PendingIntent? =
    if (Build.VERSION.SDK_INT >= 33) getParcelable(key, PendingIntent::class.java) else getParcelable(key) as? PendingIntent

  @Suppress("DEPRECATION")
  private fun Bundle.uriList(key: String): List<Uri> =
    (if (Build.VERSION.SDK_INT >= 33) getParcelableArrayList(key, Uri::class.java) else getParcelableArrayList<Uri>(key))
      .orEmpty()
}
