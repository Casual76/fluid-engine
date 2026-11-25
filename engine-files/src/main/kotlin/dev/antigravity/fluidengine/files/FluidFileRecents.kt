package dev.antigravity.fluidengine.files

import android.content.Context
import android.net.Uri

import androidx.annotation.VisibleForTesting
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import dev.antigravity.fluidengine.ui.fluid.icons.FluidIcons
import dev.antigravity.fluidengine.ui.fluid.icons.History
import dev.antigravity.fluidengine.viewer.resumeTargetExists
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileNotFoundException
import java.util.concurrent.Executors

/**
 * F8.18 · **Recenti**: i file aperti di recente dallo sfoglia-file, il più recente in cima, come la scheda «Recenti» di
 * File. Al più [maxEntries] (di serie 60); un file riaperto torna in cima, una sola volta.
 *
 * Si salvano in un file corto in `noBackupFilesDir` (un recente è un indirizzo: dopo un ripristino potrebbe non esserci),
 * scritto fuori dal thread principale. **Un file che non c'è più esce**: [prune] chiede a ogni indirizzo se esiste
 * (lo stesso controllo della ripresa, `resumeTargetExists`), e il browser lo toglie anche quando il visore non riesce
 * a leggerlo. [source] è la sorgente virtuale della pagina «Recenti».
 */
@Stable
class FluidFileRecents @VisibleForTesting internal constructor(
  private val file: File,
  val maxEntries: Int = DefaultMaxEntries,
  private val clock: () -> Long = System::currentTimeMillis,
) {
  /** Un file aperto: la sorgente da cui viene, l'elemento e quando è stato aperto. */
  @Immutable
  data class Entry(val sourceId: String, val item: FluidFileItem, val openedAtMillis: Long)

  private val io = Executors.newSingleThreadExecutor { Thread(it, "FluidFileRecents").apply { isDaemon = true } }

  // Il file è corto (al più qualche KB): si legge una volta, alla creazione, e poi si scrive solo in sottofondo.
  private var state by mutableStateOf(load())

  /** I recenti, dal più recente. Stato di Compose: chi lo legge si ricompone quando cambia. */
  val entries: List<Entry> get() = state

  /** Mette [item] di [sourceId] in cima ai recenti. Una cartella o un file senza indirizzo non entrano. */
  fun record(sourceId: String, item: FluidFileItem) {
    if (item.isFolder || item.uri == null) return
    val entry = Entry(sourceId, item, clock())
    update { list -> (listOf(entry) + list.filterNot { it.item.uri == item.uri }).take(maxEntries) }
  }

  /** Toglie il file a [uri] dai recenti (cancellato, o non più leggibile). */
  fun forget(uri: Uri) = update { list -> list.filterNot { it.item.uri == uri } }

  /** Svuota i recenti. */
  fun clear() = update { emptyList() }

  /**
   * Toglie i recenti i cui file non ci sono più: [exists] risponde vero, falso o null (non si sa: il recente resta).
   * Di serie si chiede all'indirizzo. Restituisce quanti sono usciti.
   */
  suspend fun prune(context: Context, exists: suspend (Uri) -> Boolean? = { uri ->
    withContext(Dispatchers.IO) { resumeTargetExists(context.applicationContext, uri) }
  }): Int {
    val gone = entries.mapNotNull { entry -> entry.item.uri?.takeIf { exists(it) == false } }.toSet()
    if (gone.isEmpty()) return 0
    update { list -> list.filterNot { it.item.uri in gone } }
    return gone.size
  }

  /** La sorgente della pagina «Recenti»: i file in ordine d'apertura («Data») o nell'ordine scelto. */
  val source: FluidFileSource = object : FluidFileSource {
    override val id: String get() = SourceId
    override val title: String get() = words.recents
    override val icon: ImageVector get() = FluidIcons.History
    override val sorts: Boolean get() = true

    override suspend fun list(query: FluidFileQuery): FluidFilePage {
      val list = entries
      val items = if (query.sort.key == FluidFileSortKey.Date) {
        val byOpening = list.map { it.item }
        if (query.sort.descending) byOpening else byOpening.reversed()
      } else {
        list.map { it.item }.sortedWith(query.sort.comparator())
      }
      return items.page(query)
    }
  }

  /** Da quale sorgente viene il recente [item] (per aprirlo con le stesse regole). */
  fun sourceOf(item: FluidFileItem): String? = entries.firstOrNull { it.item.uri == item.uri }?.sourceId

  internal var words: FluidFileBrowserStrings = FluidFileBrowserStrings()

  /** Aspetta che le scritture in sottofondo siano finite. Per le prove. */
  @VisibleForTesting
  internal fun awaitWrites() {
    io.submit { }.get()
  }

  private fun update(change: (List<Entry>) -> List<Entry>) {
    val next = change(entries)
    if (next == state) return
    state = next
    io.execute { write(next) }
  }

  private fun load(): List<Entry> {
    val text = try {
      String(file.readBytes(), Charsets.UTF_8)
    } catch (_: FileNotFoundException) {
      return emptyList()
    } catch (_: Exception) {
      return emptyList()
    }
    return text.lineSequence().mapNotNull(::decode).take(maxEntries).toList()
  }

  private fun write(list: List<Entry>) {
    // Tutto o niente: un file accanto, poi lo spostamento atomico al suo posto (AtomicFile, sulla JVM di Windows delle
    // prove, non sostituisce un file che esiste).
    file.parentFile?.mkdirs()
    val partial = File(file.parentFile, file.name + ".parte")
    try {
      partial.writeBytes(list.joinToString("\n", postfix = "\n") { encode(it) }.toByteArray(Charsets.UTF_8))
      java.nio.file.Files.move(partial.toPath(), file.toPath(), java.nio.file.StandardCopyOption.REPLACE_EXISTING,
        java.nio.file.StandardCopyOption.ATOMIC_MOVE)
    } catch (_: Exception) {
      partial.delete()
    }
  }

  private fun encode(entry: Entry): String = with(entry.item) {
    listOf(entry.sourceId, id, name, mimeType.orEmpty(), sizeBytes?.toString().orEmpty(),
      modifiedAtMillis?.toString().orEmpty(), uri?.toString().orEmpty(), entry.openedAtMillis.toString())
      .joinToString("\t") { it.replace('\t', ' ').replace('\n', ' ') }
  }

  private fun decode(line: String): Entry? {
    val p = line.split('\t')
    if (p.size < 8 || p[6].isEmpty()) return null
    val item = FluidFileItem(id = p[1], name = p[2], mimeType = p[3].ifEmpty { null }, sizeBytes = p[4].toLongOrNull(),
      modifiedAtMillis = p[5].toLongOrNull(), uri = Uri.parse(p[6]))
    return Entry(p[0], item, p[7].toLongOrNull() ?: 0L)
  }

  companion object {
    /** I recenti tenuti: 60, a occhio (25 novembre 2026). */
    const val DefaultMaxEntries: Int = 60
    /** L'id della sorgente virtuale dei recenti. */
    const val SourceId: String = "recenti"
    private val stores = HashMap<String, FluidFileRecents>()

    /** I recenti del processo, nel `noBackupFilesDir` dell'app. */
    fun get(context: Context): FluidFileRecents {
      val file = File(File(context.applicationContext.noBackupFilesDir, "fluid-files"), "recents-v1.tsv")
      return synchronized(stores) { stores.getOrPut(file.absolutePath) { FluidFileRecents(file) } }
    }
  }
}

/** I recenti del processo ([FluidFileRecents.get]). */
@Composable
fun rememberFluidFileRecents(): FluidFileRecents {
  val context = LocalContext.current
  return remember(context) { FluidFileRecents.get(context) }
}
