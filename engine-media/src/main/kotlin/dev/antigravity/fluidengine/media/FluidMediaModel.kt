package dev.antigravity.fluidengine.media

import android.app.PendingIntent
import android.net.Uri
import androidx.compose.runtime.Immutable
import dev.antigravity.fluidengine.ui.fluid.viewer.FluidFileInfo

// F8.14 · Il modello del selettore di foto e video (`scelta-foto-e-video`): cosa si sceglie, da dove, cosa torna all'app.
// Le forme sono classi e interfacce sigillate di un modulo nuovo; i tipi che un domani crescono (il tipo di un elemento,
// il ruolo di un album, il motivo di un rifiuto) sono classi con costanti e non enum, cosi' una voce nuova non rompe i
// `when` delle app (regola 3.11).

/**
 * Il tipo di un elemento: una foto o un video. Una classe e non un enum: un tipo nuovo (la foto dal vivo, il documento
 * fotografato) si aggiunge senza rompere chi c'e'. Due tipi con lo stesso [id] sono uguali.
 */
@Immutable
class FluidMediaKind private constructor(val id: String) {
  override fun equals(other: Any?): Boolean = other is FluidMediaKind && other.id == id
  override fun hashCode(): Int = id.hashCode()
  override fun toString(): String = "FluidMediaKind($id)"

  companion object {
    /** Una foto (anche animata: GIF, WebP). */
    val Photo: FluidMediaKind = FluidMediaKind("photo")

    /** Un video, con la sua durata. */
    val Video: FluidMediaKind = FluidMediaKind("video")

    /** Foto e video: cio' che chiede un selettore di serie. */
    val PhotosAndVideos: Set<FluidMediaKind> = setOf(Photo, Video)

    /** Il tipo con l'[id] dato: [Photo] e [Video] sono quelli di serie, un altro id resta com'e'. */
    fun of(id: String): FluidMediaKind = when (id) {
      Photo.id -> Photo
      Video.id -> Video
      else -> FluidMediaKind(id)
    }

    /** Il tipo di un file dal suo MIME: video se `video/…`, foto altrimenti. */
    fun from(mimeType: String?): FluidMediaKind =
      if (mimeType?.trim()?.lowercase()?.startsWith("video/") == true) Video else Photo
  }
}

/**
 * Un elemento di una sorgente: una foto o un video, come la griglia lo mostra e la sorgente lo consegna. Non porta
 * immagini: la miniatura la chiede la griglia alla sorgente ([FluidMediaSource.thumbnail]).
 *
 * @property source l'id della sorgente ([FluidMediaSource.id]).
 * @property id l'id dell'elemento nella sorgente: opaco, stabile finche' l'elemento esiste. Per una sorgente di un'altra
 *   app non e' mai un percorso o un nome: e' un gettone che solo lei sa leggere.
 * @property takenAtMillis quando e' stata scattata (o aggiunta, se non si sa), in millisecondi dall'epoca.
 * @property backupOnly l'elemento sta **solo nel backup** (MyCloud Gallery: «solo sul NAS»), non sul telefono: la griglia
 *   lo segna con la nuvola, la consegna puo' volere la rete e un'attesa.
 * @property description cosa dice TalkBack dell'elemento, se la sorgente lo sa («Spiaggia al tramonto»); null: il tipo e
 *   la data.
 */
@Immutable
data class FluidMediaItem(
  val source: String,
  val id: String,
  val kind: FluidMediaKind = FluidMediaKind.Photo,
  val mimeType: String? = null,
  val width: Int? = null,
  val height: Int? = null,
  val durationMillis: Long? = null,
  val takenAtMillis: Long? = null,
  val sizeBytes: Long? = null,
  val name: String? = null,
  val backupOnly: Boolean = false,
  val favorite: Boolean = false,
  val description: String? = null,
) {
  /** La chiave della scelta: la sorgente e l'id, in una stringa che un `Bundle` sa salvare. */
  val key: String get() = fluidMediaKey(source, id)

  /** Larghezza su altezza, se note. */
  val aspectRatio: Float?
    get() = if (width != null && height != null && width > 0 && height > 0) width.toFloat() / height else null
}

/** La chiave di scelta di un elemento ([FluidMediaItem.key]). */
fun fluidMediaKey(source: String, id: String): String = "$source/$id"

/**
 * Il ruolo di un album, per l'icona e l'ordine: gli album «intelligenti» di Foto (Recenti, Preferiti, Video, Screenshot)
 * e quelli di una sorgente col backup (Solo nel backup). Una classe con costanti: un ruolo nuovo non rompe nessuno.
 */
@Immutable
class FluidMediaAlbumRole private constructor(val id: String) {
  override fun equals(other: Any?): Boolean = other is FluidMediaAlbumRole && other.id == id
  override fun hashCode(): Int = id.hashCode()
  override fun toString(): String = "FluidMediaAlbumRole($id)"

  companion object {
    /** Tutto, dal piu' recente («Recenti»). */
    val All: FluidMediaAlbumRole = FluidMediaAlbumRole("all")
    val Favorites: FluidMediaAlbumRole = FluidMediaAlbumRole("favorites")
    val Videos: FluidMediaAlbumRole = FluidMediaAlbumRole("videos")
    val Screenshots: FluidMediaAlbumRole = FluidMediaAlbumRole("screenshots")
    val Camera: FluidMediaAlbumRole = FluidMediaAlbumRole("camera")

    /** Cio' che sta solo nel backup (MyCloud Gallery: «solo sul NAS»). */
    val BackupOnly: FluidMediaAlbumRole = FluidMediaAlbumRole("backup-only")

    /** Un album fatto dalla persona o una cartella. */
    val User: FluidMediaAlbumRole = FluidMediaAlbumRole("user")

    fun of(id: String): FluidMediaAlbumRole = listOf(All, Favorites, Videos, Screenshots, Camera, BackupOnly, User)
      .firstOrNull { it.id == id } ?: FluidMediaAlbumRole(id)
  }
}

/**
 * Un album di una sorgente. [count] e' quanti elementi ha, se la sorgente lo sa senza contare a lungo; [cover] e'
 * l'elemento della copertina (di solito il piu' recente).
 */
@Immutable
data class FluidMediaAlbum(
  val source: String,
  val id: String,
  val title: String,
  val count: Int? = null,
  val cover: FluidMediaItem? = null,
  val role: FluidMediaAlbumRole = FluidMediaAlbumRole.User,
)

/**
 * Cosa si chiede a una sorgente: un album (null: tutto, dal piu' recente), un testo da cercare (null: nessuna ricerca) e
 * i tipi. Con [text] una sorgente con la ricerca semantica ([FluidMediaSourceCapabilities.semanticSearch]) cerca per
 * significato («mare d'estate», «Anna al parco»); le altre per nome o per cartella.
 *
 * @property localOnly vero: niente elementi che stanno solo nel backup (l'`EXTRA_LOCAL_ONLY` di Android).
 */
@Immutable
data class FluidMediaQuery(
  val album: String? = null,
  val text: String? = null,
  val kinds: Set<FluidMediaKind> = FluidMediaKind.PhotosAndVideos,
  val localOnly: Boolean = false,
)

/**
 * Una pagina di risultati: gli elementi, nell'ordine della sorgente (il piu' recente prima, o il piu' pertinente per una
 * ricerca), il segno della pagina dopo ([next], null alla fine) e, per una ricerca, come la sorgente l'ha capita
 * ([interpretation]: «Mare», «Agosto, tutti gli anni»), che il selettore mostra sotto il campo.
 */
@Immutable
data class FluidMediaPage(
  val items: List<FluidMediaItem>,
  val next: String? = null,
  val total: Int? = null,
  val interpretation: List<String> = emptyList(),
)

/**
 * Cosa sa fare una sorgente.
 *
 * @property search cerca per testo (anche solo per nome o cartella).
 * @property semanticSearch cerca per significato (MyCloud Gallery, F12): il campo lo dice nel suggerimento.
 * @property albums ha degli album.
 * @property backupOnly ha elementi che stanno solo nel backup ([FluidMediaItem.backupOnly]).
 * @property maxDelivery quanti elementi consegna al piu' in una volta (null: nessun tetto suo).
 */
@Immutable
data class FluidMediaSourceCapabilities(
  val search: Boolean = false,
  val semanticSearch: Boolean = false,
  val albums: Boolean = true,
  val backupOnly: Boolean = false,
  val maxDelivery: Int? = null,
)

/**
 * L'accesso a una sorgente in questo momento. Il selettore lo legge come stato e mostra la cosa giusta: la griglia, la
 * fascia «solo alcune foto», il tasto che chiede il permesso, lo sblocco, il motivo per cui non c'e'.
 *
 * Una interfaccia sigillata di un modulo nuovo: chi la legge in un `when` mette un `else`, perche' uno stato nuovo potra'
 * arrivare.
 */
sealed interface FluidMediaAccess {
  /** Tutto. */
  data object Full : FluidMediaAccess

  /**
   * Solo una parte: le foto «solo alcune» di Android 14 (`READ_MEDIA_VISUAL_USER_SELECTED`). La sorgente mostra quelle
   * concesse; il selettore lo dice e offre di sceglierne altre.
   */
  data object Limited : FluidMediaAccess

  /** Nessun accesso: il permesso non c'e'. [forever] se Android non lo chiede piu' (si va nelle impostazioni). */
  data class NotAllowed(val forever: Boolean = false) : FluidMediaAccess

  /**
   * La sorgente e' chiusa (il blocco dell'app di MyCloud Gallery): [unlock] apre la sua schermata di sblocco, poi il
   * selettore rilegge l'accesso. Niente si elenca finche' e' chiusa.
   */
  data class Locked(val unlock: PendingIntent? = null) : FluidMediaAccess

  /**
   * La sorgente non c'e' adesso: [reason] e' un codice ([FluidMediaSourceContract.UNAVAILABLE_NOT_READY],
   * [FluidMediaSourceContract.UNAVAILABLE_STOPPED], [FluidMediaSourceContract.UNAVAILABLE_GONE], o uno della sorgente).
   */
  data class Unavailable(val reason: String) : FluidMediaAccess
}

/** A che punto e' una consegna lunga: elementi fatti su totale, e i byte di quello in corso (se noti). */
@Immutable
data class FluidMediaProgress(val done: Int, val total: Int, val bytesDone: Long = 0, val bytesTotal: Long = 0) {
  /** La frazione fatta, se si sa. */
  val fraction: Float?
    get() = if (total <= 0) null else ((done + if (bytesTotal > 0) bytesDone.toFloat() / bytesTotal else 0f) / total).coerceIn(0f, 1f)
}

/**
 * Perche' una consegna non e' avvenuta. Una classe con costanti: un motivo nuovo non rompe nessuno.
 *
 * @property limit per [tooMany], il tetto.
 */
@Immutable
class FluidMediaRefusal private constructor(val code: String, val limit: Int? = null) {
  override fun equals(other: Any?): Boolean = other is FluidMediaRefusal && other.code == code && other.limit == limit
  override fun hashCode(): Int = code.hashCode() * 31 + (limit ?: 0)
  override fun toString(): String = "FluidMediaRefusal($code${limit?.let { ", $it" }.orEmpty()})"

  companion object {
    /** Il backup non risponde (il NAS di casa spento o lontano): gli elementi solo nel backup non si possono avere ora. */
    val Unreachable: FluidMediaRefusal = FluidMediaRefusal("unreachable")

    /** Fra quelli scelti nessuno si puo' consegnare adesso (spariti, nascosti dopo che la griglia li mostrava). */
    val NothingAvailable: FluidMediaRefusal = FluidMediaRefusal("nothing-available")

    /** La posizione non si e' potuta togliere (MyCloud con «Rimuovi la posizione» acceso): niente e' uscito. */
    val LocationNotRemoved: FluidMediaRefusal = FluidMediaRefusal("location-not-removed")

    /** Un errore della sorgente. */
    val Failed: FluidMediaRefusal = FluidMediaRefusal("failed")

    /** Troppi elementi insieme: al piu' [limit]. */
    fun tooMany(limit: Int): FluidMediaRefusal = FluidMediaRefusal(TooManyCode, limit)

    /** Il motivo col [code] dato (dal contratto fra app). */
    fun of(code: String, limit: Int? = null): FluidMediaRefusal = when (code) {
      Unreachable.code -> Unreachable
      NothingAvailable.code -> NothingAvailable
      LocationNotRemoved.code -> LocationNotRemoved
      Failed.code -> Failed
      TooManyCode -> tooMany(limit ?: 0)
      else -> FluidMediaRefusal(code, limit)
    }

    internal const val TooManyCode = "too-many"
  }
}

/**
 * L'esito di una consegna ([FluidMediaSource.deliver]).
 *
 * Una interfaccia sigillata di un modulo nuovo: chi la legge in un `when` mette un `else`.
 */
sealed interface FluidMediaDelivery {
  /**
   * Gli indirizzi, nell'ordine chiesto (l'ordine della scelta). [skipped]: quanti elementi scelti non c'erano piu' (spariti,
   * nascosti dopo che la griglia li mostrava).
   */
  class Ready(val media: List<FluidPickedMedia>, val skipped: Int = 0) : FluidMediaDelivery {
    override fun toString(): String = "Ready(${media.size}, skipped=$skipped)"
  }

  /** Niente consegnato: [reason] dice perche'; [culprit] e' l'id dell'elemento che l'ha fermata, se e' uno solo. */
  class Refused(val reason: FluidMediaRefusal, val culprit: String? = null) : FluidMediaDelivery {
    override fun toString(): String = "Refused($reason)"
  }

  /** La sorgente si e' chiusa nel frattempo: [unlock] la riapre, poi si riprova. */
  class NeedsUnlock(val unlock: PendingIntent? = null) : FluidMediaDelivery
}

/**
 * Cio' che l'app riceve: l'indirizzo da leggere (con il permesso di lettura) e le informazioni del file, come le usano i
 * visori e i tasselli dei file ([FluidFileInfo]).
 *
 * @property uri un `content://` leggibile dall'app: del MediaStore (il telefono), del Photo Picker di Android, o del
 *   provider della sorgente che l'ha consegnato, col permesso di lettura dato a questa app. Per tenerlo oltre la vita del
 *   processo l'app ne fa una copia (la lettura data da un'altra app puo' scadere).
 * @property source l'id della sorgente ([FluidMediaSource.id]); [FluidSystemPhotoPicker.SourceId] per il Photo Picker di
 *   Android.
 * @property item l'elemento da cui viene, se e' venuto dal selettore dell'engine.
 */
@Immutable
data class FluidPickedMedia(
  val uri: Uri,
  val info: FluidFileInfo,
  val kind: FluidMediaKind = FluidMediaKind.from(info.mimeType),
  val width: Int? = null,
  val height: Int? = null,
  val durationMillis: Long? = null,
  val source: String,
  val item: FluidMediaItem? = null,
)

/**
 * Cosa chiede l'app al selettore.
 *
 * @property kinds foto, video o tutti e due.
 * @property limit quante scelte al piu' (`selectionLimit` di iOS): 1, di serie come in iOS, fa scegliere e tornare con un
 *   tocco; di piu' fa scegliere in ordine e confermare.
 * @property ordered le scelte portano il numero d'ordine (vero di serie con [limit] > 1, come chiede la scheda: «scelta
 *   multipla nell'ordine del tocco»); falso: la spunta.
 * @property mimeTypes i tipi precisi ammessi («image/jpeg», «image/ *» senza lo spazio); null: tutti quelli di [kinds].
 * @property localOnly niente elementi che stanno solo nel backup.
 */
@Immutable
data class FluidMediaRequest(
  val kinds: Set<FluidMediaKind> = FluidMediaKind.PhotosAndVideos,
  val limit: Int = 1,
  val ordered: Boolean = limit > 1,
  val mimeTypes: List<String>? = null,
  val localOnly: Boolean = false,
) {
  init {
    require(limit >= 1) { "FluidMediaRequest: si sceglie almeno un elemento" }
    require(kinds.isNotEmpty()) { "FluidMediaRequest: serve almeno un tipo" }
  }

  /** La domanda di serie per una sorgente, con l'album e il testo dati. */
  fun query(album: String? = null, text: String? = null): FluidMediaQuery =
    FluidMediaQuery(album = album, text = text?.trim()?.takeIf { it.isNotEmpty() }, kinds = kinds, localOnly = localOnly)

  /** Vero se un elemento di tipo [mimeType] va bene per questa richiesta. Un tipo ignoto passa se i tipi non sono precisi. */
  fun accepts(item: FluidMediaItem): Boolean {
    if (item.kind !in kinds) return false
    if (localOnly && item.backupOnly) return false
    val types = mimeTypes ?: return true
    val mime = item.mimeType?.substringBefore(';')?.trim()?.lowercase() ?: return types.all { it.endsWith("/*") }
    return types.any { t ->
      val p = t.trim().lowercase()
      p == "*/*" || (p.endsWith("/*") && mime.substringBefore('/') == p.substringBefore('/')) || p == mime
    }
  }

  companion object {
    /** Una foto sola, come la foto di un profilo. */
    val SinglePhoto: FluidMediaRequest = FluidMediaRequest(kinds = setOf(FluidMediaKind.Photo), limit = 1)

    /** Fino a [limit] foto e video, in ordine: gli allegati di una mail. */
    fun attachments(limit: Int = 20): FluidMediaRequest = FluidMediaRequest(limit = limit)
  }
}
