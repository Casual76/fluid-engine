package dev.antigravity.fluidengine.media

import android.app.PendingIntent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Shader
import android.net.Uri
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import dev.antigravity.fluidengine.ui.fluid.viewer.FluidFileInfo
import kotlinx.coroutines.delay
import java.text.Normalizer
import java.time.Instant
import java.time.ZoneOffset
import java.util.Locale

/**
 * Una sorgente finta (F8.14), per le prove e per la galleria: un archivio in memoria con album, elementi solo nel backup
 * e una **ricerca per significato finta** (parole contro etichette, e le stagioni contro la data), che si comporta come
 * fara' MyCloud Gallery dietro il contratto ([FluidMediaSourceContract]). Si puo' chiudere ([access] =
 * [FluidMediaAccess.Locked]), spegnere il backup ([backupReachable] falso: gli elementi solo nel backup non si consegnano,
 * [FluidMediaRefusal.Unreachable]) e rallentare ([latencyMillis]).
 *
 * Le miniature sono disegnate al momento (una sfumatura dei due colori dell'elemento), o le da' [thumbnails]. La consegna
 * da' indirizzi `content://<[uriAuthority]>/<id>` che nessuno apre: servono a contare e a mettere in ordine.
 */
@Stable
class FluidFakeMediaSource(
  override val id: String,
  override val label: String,
  entries: List<Entry>,
  private val albumList: List<Album> = emptyList(),
  override val capabilities: FluidMediaSourceCapabilities = FluidMediaSourceCapabilities(
    search = true, semanticSearch = true, albums = true, backupOnly = entries.any { it.item.backupOnly },
  ),
  initialAccess: FluidMediaAccess = FluidMediaAccess.Full,
  private val thumbnails: ((FluidMediaItem, Int) -> Bitmap?)? = null,
  private val uriAuthority: String = "dev.antigravity.fluidengine.media.fake",
) : FluidMediaSource {

  /**
   * Un elemento della sorgente finta: l'elemento, le etichette della ricerca per significato («mare», «anna», «cane»), gli
   * album in cui sta e i due colori della sua miniatura.
   */
  class Entry(
    val item: FluidMediaItem,
    val tags: Set<String> = emptySet(),
    val albums: Set<String> = emptySet(),
    val colors: Pair<Int, Int> = 0xFF5DBB8F.toInt() to 0xFF1F6E8C.toInt(),
  )

  /** Un album della sorgente finta: gli elementi sono quelli che lo nominano in [Entry.albums]. */
  class Album(val id: String, val title: String, val role: FluidMediaAlbumRole = FluidMediaAlbumRole.User)

  private val all: List<Entry> = entries.sortedByDescending { it.item.takenAtMillis ?: 0L }

  /** Gli elementi, dal piu' recente (per farne una copia con altre regole, o per le prove). */
  val entries: List<Entry> get() = all

  /** Gli album dichiarati (oltre a «Libreria» e «Solo nel backup», che la sorgente fa da se'). */
  val declaredAlbums: List<Album> get() = albumList

  override var access: FluidMediaAccess by mutableStateOf(initialAccess)

  /** Falso: il backup (il NAS) non risponde, e gli elementi solo nel backup non si consegnano. */
  var backupReachable: Boolean by mutableStateOf(true)

  /** Un'attesa prima di ogni risposta, per vedere gli stati di caricamento. */
  var latencyMillis: Long = 0L

  /** Le consegne fatte, una lista di id per consegna, nell'ordine ricevuto (per le prove). */
  val deliveries: List<List<String>> get() = deliveredIds.toList()
  private val deliveredIds = ArrayList<List<String>>()

  /** Le domande ricevute (per le prove). */
  val queries: List<FluidMediaQuery> get() = askedQueries.toList()
  private val askedQueries = ArrayList<FluidMediaQuery>()

  /** Quante volte e' stato riletto l'accesso. */
  var refreshCount: Int = 0
    private set

  override suspend fun refresh() {
    refreshCount++
  }

  override suspend fun albums(): List<FluidMediaAlbum> {
    wait()
    if (!access.allowsListing()) return emptyList()
    val out = ArrayList<FluidMediaAlbum>()
    out += FluidMediaAlbum(id, AlbumAll, "Libreria", all.size, all.firstOrNull()?.item, FluidMediaAlbumRole.All)
    val backup = all.filter { it.item.backupOnly }
    if (backup.isNotEmpty()) {
      out += FluidMediaAlbum(id, AlbumBackupOnly, "Solo nel backup", backup.size, backup.first().item, FluidMediaAlbumRole.BackupOnly)
    }
    for (album in albumList) {
      val inside = all.filter { album.id in it.albums }
      out += FluidMediaAlbum(id, album.id, album.title, inside.size, inside.firstOrNull()?.item, album.role)
    }
    return out
  }

  override suspend fun page(query: FluidMediaQuery, cursor: String?, limit: Int): FluidMediaPage {
    wait()
    askedQueries += query
    if (!access.allowsListing()) return FluidMediaPage(emptyList())
    var list = all.filter { it.item.kind in query.kinds && !(query.localOnly && it.item.backupOnly) }
    list = when (query.album) {
      null, AlbumAll -> list
      AlbumBackupOnly -> list.filter { it.item.backupOnly }
      else -> list.filter { query.album in it.albums }
    }
    val interpretation = ArrayList<String>()
    val text = query.text
    if (text != null) {
      val (matcher, labels) = fakeMeaning(text)
      interpretation += labels
      list = list.filter(matcher)
    }
    val offset = cursor?.toIntOrNull()?.coerceAtLeast(0) ?: 0
    val slice = list.drop(offset).take(limit)
    return FluidMediaPage(
      items = slice.map { it.item },
      next = if (offset + slice.size < list.size) (offset + slice.size).toString() else null,
      total = list.size,
      interpretation = interpretation,
    )
  }

  override suspend fun thumbnail(item: FluidMediaItem, sizePx: Int): Bitmap? {
    wait()
    if (!access.allowsListing()) return null
    thumbnails?.invoke(item, sizePx)?.let { return it }
    val entry = all.firstOrNull { it.item.id == item.id } ?: return null
    val side = sizePx.coerceIn(16, 512)
    val w = side
    val h = item.aspectRatio?.let { (side / it).toInt().coerceIn(16, 1024) } ?: side
    return Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888).also { bitmap ->
      val paint = Paint().apply {
        shader = LinearGradient(0f, 0f, w.toFloat(), h.toFloat(), entry.colors.first, entry.colors.second, Shader.TileMode.CLAMP)
      }
      Canvas(bitmap).drawRect(0f, 0f, w.toFloat(), h.toFloat(), paint)
    }
  }

  override suspend fun deliver(
    items: List<FluidMediaItem>,
    request: FluidMediaRequest,
    onProgress: (FluidMediaProgress) -> Unit,
  ): FluidMediaDelivery {
    wait()
    when (val a = access) {
      is FluidMediaAccess.Locked -> return FluidMediaDelivery.NeedsUnlock(a.unlock)
      FluidMediaAccess.Full, FluidMediaAccess.Limited -> Unit
      else -> return FluidMediaDelivery.Refused(FluidMediaRefusal.NothingAvailable)
    }
    val cap = capabilities.maxDelivery
    if (cap != null && items.size > cap) return FluidMediaDelivery.Refused(FluidMediaRefusal.tooMany(cap))
    // Come MyCloud Gallery: il backup che non risponde si dice prima di consegnare qualunque cosa.
    if (!backupReachable && items.any { it.backupOnly }) return FluidMediaDelivery.Refused(FluidMediaRefusal.Unreachable)
    var skipped = 0
    val out = ArrayList<FluidPickedMedia>(items.size)
    items.forEachIndexed { index, wanted ->
      onProgress(FluidMediaProgress(index, items.size))
      // La vista di adesso decide: un elemento tolto dalla sorgente non esce.
      val entry = all.firstOrNull { it.item.id == wanted.id }
      if (entry == null || !request.accepts(entry.item)) {
        skipped++
        return@forEachIndexed
      }
      val item = entry.item
      out += FluidPickedMedia(
        uri = Uri.parse("content://$uriAuthority/${Uri.encode(item.id)}"),
        info = FluidFileInfo(
          name = item.name ?: "${item.id}.${if (item.kind == FluidMediaKind.Video) "mp4" else "jpg"}",
          mimeType = item.mimeType ?: if (item.kind == FluidMediaKind.Video) "video/mp4" else "image/jpeg",
          sizeBytes = item.sizeBytes,
          modifiedAtMillis = item.takenAtMillis,
        ),
        kind = item.kind,
        width = item.width,
        height = item.height,
        durationMillis = item.durationMillis,
        source = id,
        item = item,
      )
    }
    onProgress(FluidMediaProgress(items.size, items.size))
    deliveredIds += out.map { it.item!!.id }
    return if (out.isEmpty()) FluidMediaDelivery.Refused(FluidMediaRefusal.NothingAvailable) else FluidMediaDelivery.Ready(out, skipped)
  }

  /** I due colori della miniatura di [item], per una griglia che la disegna da se'. */
  fun colorsOf(item: FluidMediaItem): Pair<Int, Int>? = all.firstOrNull { it.item.id == item.id }?.colors

  /** Chiude la sorgente come il blocco dell'app di MyCloud Gallery; [unlock] e' l'azione di sblocco. */
  fun lock(unlock: PendingIntent? = null) {
    access = FluidMediaAccess.Locked(unlock)
  }

  private suspend fun wait() {
    if (latencyMillis > 0) delay(latencyMillis)
  }

  /** La ricerca per significato finta: le parole di [text] contro le etichette, le stagioni contro la data. */
  private fun fakeMeaning(text: String): Pair<(Entry) -> Boolean, List<String>> {
    val words = normalize(text).split(Regex("[^a-z0-9]+")).filter { it.length > 1 && it !in StopWords }
    if (words.isEmpty()) return Pair({ _: Entry -> true }, emptyList())
    val tests = ArrayList<(Entry) -> Boolean>()
    val labels = ArrayList<String>()
    for (word in words) {
      val season = Seasons[word]
      if (season != null) {
        tests += { e -> e.item.takenAtMillis?.let { monthOf(it) in season.second } == true }
        labels += season.first
        continue
      }
      val synonyms = Synonyms[word] ?: setOf(word)
      tests += { e -> e.tags.any { tag -> synonyms.any { s -> normalize(tag).startsWith(s) } } }
      labels += word.replaceFirstChar { it.titlecase(Locale.ITALIAN) }
    }
    return Pair({ e: Entry -> tests.all { it(e) } }, labels)
  }

  companion object {
    /** L'album di tutto. */
    const val AlbumAll: String = "all"

    /** L'album di cio' che sta solo nel backup. */
    const val AlbumBackupOnly: String = "backup-only"

    private val StopWords = setOf("di", "del", "della", "dei", "al", "alla", "allo", "ai", "il", "lo", "la", "le", "gli", "con", "in", "e", "un", "una", "da", "su", "per", "foto", "video")

    private val Synonyms: Map<String, Set<String>> = mapOf(
      "mare" to setOf("mare", "spiaggia"),
      "spiaggia" to setOf("spiaggia", "mare"),
      "montagna" to setOf("montagna", "neve", "sentiero"),
      "neve" to setOf("neve"),
      "cane" to setOf("cane"),
      "gatto" to setOf("gatto"),
      "tramonto" to setOf("tramonto"),
      "festa" to setOf("festa", "compleanno"),
      "citta" to setOf("citta"),
    )

    private val Seasons: Map<String, Pair<String, Set<Int>>> = mapOf(
      "estate" to ("Estate" to setOf(6, 7, 8)),
      "estiva" to ("Estate" to setOf(6, 7, 8)),
      "inverno" to ("Inverno" to setOf(12, 1, 2)),
      "invernale" to ("Inverno" to setOf(12, 1, 2)),
      "primavera" to ("Primavera" to setOf(3, 4, 5)),
      "autunno" to ("Autunno" to setOf(9, 10, 11)),
    )

    private fun monthOf(millis: Long): Int = Instant.ofEpochMilli(millis).atZone(ZoneOffset.UTC).monthValue

    private fun normalize(text: String): String =
      Normalizer.normalize(text.lowercase(Locale.ROOT), Normalizer.Form.NFD).replace(Regex("\\p{M}+"), "")

    /**
     * Una libreria di prova come quella di MyCloud Gallery: [count] elementi dal 2024 a oggi (uno ogni circa nove giorni),
     * un video ogni cinque, un terzo solo nel backup, con etichette (mare, montagna, Anna, cane, festa, tramonto, citta')
     * e tre album. Sempre gli stessi, a ogni chiamata: le prove ci contano.
     */
    fun sample(
      id: String = "mycloud",
      label: String = "MyCloud Gallery",
      count: Int = 60,
      newestMillis: Long = 1_764_000_000_000L,
    ): FluidFakeMediaSource {
      val topics = listOf(
        setOf("mare", "spiaggia") to (0xFF6DB3F2.toInt() to 0xFF1F6E8C.toInt()),
        setOf("montagna", "sentiero") to (0xFF7FD1AE.toInt() to 0xFF2E6B4F.toInt()),
        setOf("anna", "parco") to (0xFFFFD3D3.toInt() to 0xFFE0607E.toInt()),
        setOf("cane") to (0xFFFFE3B3.toInt() to 0xFFC98A2B.toInt()),
        setOf("festa", "compleanno") to (0xFFE3D7FF.toInt() to 0xFF7A5BD8.toInt()),
        setOf("tramonto", "mare") to (0xFFFFB340.toInt() to 0xFF9C3D54.toInt()),
        setOf("citta") to (0xFFB8C4D6.toInt() to 0xFF3A4A63.toInt()),
        setOf("neve", "montagna") to (0xFFF2F6FA.toInt() to 0xFF8BA3BD.toInt()),
      )
      val day = 86_400_000L
      val entries = (0 until count).map { i ->
        val (tags, colors) = topics[(i * 3 + i / 4) % topics.size]
        val video = i % 5 == 3
        val taken = newestMillis - i * 9L * day - (i % 3) * 3_600_000L
        val portrait = i % 4 == 1
        val item = FluidMediaItem(
          source = id,
          id = "m%03d".format(Locale.ROOT, i),
          kind = if (video) FluidMediaKind.Video else FluidMediaKind.Photo,
          mimeType = if (video) "video/mp4" else "image/jpeg",
          width = if (portrait) 3024 else 4032,
          height = if (portrait) 4032 else 3024,
          durationMillis = if (video) (8 + (i * 7) % 75) * 1000L else null,
          takenAtMillis = taken,
          sizeBytes = if (video) 18_000_000L + i * 1_000_000L else 2_400_000L + i * 10_000L,
          name = if (video) "VID_%04d.mp4".format(Locale.ROOT, 1000 + i) else "IMG_%04d.jpg".format(Locale.ROOT, 1000 + i),
          backupOnly = i % 3 == 2,
          favorite = i % 7 == 0,
          description = tags.first().replaceFirstChar { it.titlecase(Locale.ITALIAN) },
        )
        val albums = buildSet {
          if ("mare" in tags || "montagna" in tags) add("vacanze")
          if ("anna" in tags || "festa" in tags) add("famiglia")
          if ("cane" in tags) add("pippo")
        }
        Entry(item, tags, albums, colors)
      }
      return FluidFakeMediaSource(
        id = id,
        label = label,
        entries = entries,
        albumList = listOf(Album("vacanze", "Vacanze"), Album("famiglia", "Famiglia"), Album("pippo", "Pippo")),
      )
    }
  }
}
