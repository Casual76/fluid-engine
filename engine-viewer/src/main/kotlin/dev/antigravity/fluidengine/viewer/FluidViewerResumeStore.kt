package dev.antigravity.fluidengine.viewer

import android.content.ComponentCallbacks2
import android.content.Context
import android.content.res.Configuration
import android.net.Uri
import android.provider.OpenableColumns
import androidx.annotation.VisibleForTesting
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import dev.antigravity.fluidengine.ui.fluid.viewer.FluidViewerPosition
import dev.antigravity.fluidengine.ui.fluid.viewer.FluidViewerResumeSlot
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.Closeable
import java.io.File
import java.io.FileNotFoundException
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.Executors
import java.util.concurrent.Future
import java.util.concurrent.ScheduledFuture
import java.util.concurrent.TimeUnit

/**
 * F8.18 · **Riprendi dallo stesso punto**: dove l'utente era in ogni documento e testo (la pagina e il punto della
 * pagina, lo zoom, la riga), salvato **in modo leggero** e **cancellato quando il file non c'è più**. Un audio o un video
 * riparte dall'inizio (F8.18b): il suo preset non legge il posto.
 *
 * - **Leggero.** Le posizioni stanno in memoria; il disco si scrive una volta sola, [DefaultSaveDelayMillis] dopo
 *   l'ultima modifica, e subito quando l'app va in background (`TRIM_MEMORY_UI_HIDDEN`). Un file di testo di poche
 *   decine di KB in `noBackupFilesDir` (una posizione non va nel backup: il file a cui si riferisce potrebbe non
 *   esserci), al più [maxEntries] posizioni, le più vecchie escono per prime.
 * - **Cancellato quando il file non c'è più.** Una volta al giorno, alla prima apertura, [prune] chiede a ogni indirizzo
 *   se esiste ancora (`file://` sul disco, `content://` al suo provider: un provider disinstallato, un permesso perso o
 *   un documento cancellato tolgono la posizione). Un file che il visore non riesce più a leggere (`Unreadable`) la perde
 *   subito, e l'app che cancella un file chiama [forget].
 * - **La revisione.** La chiave è l'indirizzo del file; la revisione (di serie peso e data, [revisionOf]) dice se è lo
 *   stesso contenuto. Una posizione di un'altra revisione non si applica da sola: il preset la offre («Riprendi da
 *   pagina 42»), come nella scheda dell'idea. Lo stesso nome con un indirizzo diverso è un altro file.
 * - **Un altro dispositivo.** [export] e [import] danno e prendono la riga di una posizione; [observe] avvisa a ogni
 *   salvataggio. Il trasporto (l'Account Pampa, `engine-sync`) è dell'app; fra due righe vince la più recente.
 *
 * Il negozio di serie è uno per processo ([get]). La prima lettura aspetta il disco (pochi KB, una volta).
 */
class FluidViewerResumeStore @VisibleForTesting internal constructor(
  private val file: File,
  val maxEntries: Int = DefaultMaxEntries,
  private val saveDelayMillis: Long = DefaultSaveDelayMillis,
  private val clock: () -> Long = System::currentTimeMillis,
) {
  /**
   * Una posizione salvata: la [key] del file, il suo indirizzo ([uri], per [prune]), la [revision] del contenuto, la
   * [position] e quando è stata salvata. [encode] la scrive in una riga separata da tabulazioni.
   */
  @Immutable
  data class Entry(
    val key: String,
    val uri: String,
    val revision: String?,
    val position: FluidViewerPosition,
    val savedAtMillis: Long,
  ) {
    fun encode(): String = listOf(key, uri, revision.orEmpty(), position.encode(), savedAtMillis.toString())
      .joinToString("\t") { it.escaped() }

    companion object {
      /** La riga di [encode]; null se non è una posizione. */
      fun decode(line: String): Entry? {
        val parts = line.split('\t')
        if (parts.size < 5) return null
        val key = parts[0].unescaped().takeIf { it.isNotEmpty() } ?: return null
        val position = FluidViewerPosition.decode(parts[3].unescaped()) ?: return null
        val saved = parts[4].toLongOrNull() ?: return null
        return Entry(key, parts[1].unescaped(), parts[2].unescaped().takeIf { it.isNotEmpty() }, position, saved)
      }
    }
  }

  private val lock = Any()
  // In ordine d'accesso: la prima voce è la meno usata, ed esce per prima oltre il tetto.
  private val entries = LinkedHashMap<String, Entry>(64, 0.75f, true)
  private var prunedAtMillis = 0L
  private val io = Executors.newSingleThreadScheduledExecutor { runnable ->
    Thread(runnable, "FluidViewerResume").apply { isDaemon = true }
  }
  private val loading: Future<*> = io.submit { load() }
  private var pending: ScheduledFuture<*>? = null
  private var dirty = false
  // R2: le scritture fallite di fila, per allungare l'attesa del tentativo dopo.
  private var failures = 0
  private val listeners = CopyOnWriteArrayList<(Entry) -> Unit>()

  /** Le posizioni salvate, dalla meno usata alla più usata. */
  val all: List<Entry> get() = ready { entries.values.toList() }

  /** La posizione del file [key], o null. */
  fun read(key: String): Entry? = ready { entries[key] }

  /**
   * Salva la [position] del file [key]. Una posizione all'inizio ([FluidViewerPosition.isStart]) non serve: toglie
   * quella che c'era. La scrittura su disco arriva più tardi, tutta insieme.
   */
  fun save(key: String, uri: String, revision: String?, position: FluidViewerPosition) {
    if (position.isStart) {
      forget(key)
      return
    }
    val entry = Entry(key, uri, revision, position, clock())
    val changed = ready {
      val before = entries[key]
      if (before?.position == entry.position && before.revision == revision && before.uri == uri) return@ready false
      entries[key] = entry
      while (entries.size > maxEntries) entries.remove(entries.keys.first())
      true
    }
    if (changed) {
      schedule()
      listeners.forEach { it(entry) }
    }
  }

  /** Toglie la posizione del file [key] (l'app ha cancellato il file, o l'utente l'ha chiuso dall'inizio). */
  fun forget(key: String) {
    if (ready { entries.remove(key) != null }) schedule()
  }

  /** Toglie le posizioni di un indirizzo, qualunque sia la chiave. */
  fun forgetUri(uri: Uri) {
    val text = uri.toString()
    if (ready { entries.values.removeAll { it.uri == text } }) schedule()
  }

  /** La riga della posizione di [key], per portarla su un altro dispositivo; null se non c'è. */
  fun export(key: String): String? = read(key)?.encode()

  /**
   * Prende la riga di un altro dispositivo ([export]). Vince la più recente: una riga più vecchia di quella che c'è non
   * cambia niente. Vero se la posizione è entrata.
   */
  fun import(record: String): Boolean {
    val entry = Entry.decode(record) ?: return false
    val accepted = ready {
      val before = entries[entry.key]
      if (before != null && before.savedAtMillis >= entry.savedAtMillis) return@ready false
      entries[entry.key] = entry
      while (entries.size > maxEntries) entries.remove(entries.keys.first())
      true
    }
    if (accepted) schedule()
    return accepted
  }

  /** Avvisa [listener] a ogni posizione salvata, per l'app che la porta altrove. Chiudere per smettere. */
  fun observe(listener: (Entry) -> Unit): Closeable {
    listeners += listener
    return Closeable { listeners -= listener }
  }

  /** Scrive subito sul disco ciò che è in memoria. Lo fa da sé quando l'app va in background. */
  fun flush() {
    synchronized(lock) {
      pending?.cancel(false)
      pending = null
    }
    io.submit { write() }.get()
  }

  /**
   * Toglie le posizioni dei file che non esistono più: [exists] dice per ogni posizione se il suo file c'è ancora
   * (vero), non c'è più (falso) o non si sa (null: la posizione resta, per esempio un provider che non risponde).
   * Di serie si chiede all'indirizzo ([resumeTargetExists]). Restituisce quante posizioni sono uscite.
   */
  suspend fun prune(exists: suspend (Entry) -> Boolean?): Int {
    val gone = all.filter { exists(it) == false }.map { it.key }.toSet()
    val removed = ready {
      prunedAtMillis = clock()
      val before = entries.size
      entries.keys.removeAll(gone)
      before - entries.size
    }
    dirty = true
    schedule()
    return removed
  }

  /** [prune] con la domanda di serie all'indirizzo di ogni file. */
  suspend fun prune(context: Context): Int {
    val app = context.applicationContext
    return prune { entry -> withContext(Dispatchers.IO) { resumeTargetExists(app, Uri.parse(entry.uri)) } }
  }

  /** Vero se è passato un giorno dall'ultima pulizia (o non c'è mai stata). */
  val pruneDue: Boolean get() = ready { clock() - prunedAtMillis >= PruneIntervalMillis }

  /**
   * Il posto della posizione di [file] per il telaio ([FluidViewerResumeSlot]): la posizione della stessa revisione si
   * applica, quella di un'altra si offre ([FluidViewerResumeSlot.stale]).
   */
  fun slot(file: FluidViewerFile, key: String = keyOf(file), revision: String? = revisionOf(file)): FluidViewerResumeSlot =
    StoreSlot(this, key, file.uri.toString(), revision, read(key))

  private class StoreSlot(
    private val store: FluidViewerResumeStore,
    private val key: String,
    private val uri: String,
    private val revision: String?,
    saved: Entry?,
  ) : FluidViewerResumeSlot {
    private val same = saved != null && (saved.revision == null || revision == null || saved.revision == revision)
    override val position: FluidViewerPosition? = saved?.position?.takeIf { same && !it.isStart }
    private var offered by mutableStateOf(saved?.position?.takeIf { !same && !it.isStart })
    override val stale: FluidViewerPosition? get() = offered
    override fun save(position: FluidViewerPosition) = store.save(key, uri, revision, position)
    override fun dismissStale() {
      offered = null
    }
  }

  private inline fun <R> ready(block: () -> R): R {
    loading.get()
    return synchronized(lock, block)
  }

  private fun schedule() {
    synchronized(lock) {
      dirty = true
      if (pending != null) return
      pending = io.schedule({
        synchronized(lock) { pending = null }
        write()
      }, saveDelayMillis, TimeUnit.MILLISECONDS)
    }
  }

  private fun load() {
    val text = try {
      String(file.readBytes(), Charsets.UTF_8)
    } catch (_: FileNotFoundException) {
      return
    } catch (_: Exception) {
      // Un file rovinato non ferma il visore: si riparte senza posizioni.
      return
    }
    synchronized(lock) {
      for (line in text.lineSequence()) {
        if (line.startsWith(Header)) {
          prunedAtMillis = line.substringAfter("pruned=", "0").trim().toLongOrNull() ?: 0L
          continue
        }
        Entry.decode(line)?.let { entries[it.key] = it }
      }
      while (entries.size > maxEntries) entries.remove(entries.keys.first())
    }
  }

  private fun write() {
    val text = synchronized(lock) {
      if (!dirty) return
      dirty = false
      buildString {
        append(Header).append(" pruned=").append(prunedAtMillis).append('\n')
        for (entry in entries.values) append(entry.encode()).append('\n')
      }
    }
    val written = writeFileAtomically(file, text.toByteArray(Charsets.UTF_8))
    synchronized(lock) {
      if (written) {
        failures = 0
        return
      }
      // R2: il disco non ha preso la scrittura (pieno un attimo, una cartella che non si crea). Le posizioni restano da
      // scrivere: un flush o l'uscita in background riprovano subito, e da sé si riprova dopo un'attesa che raddoppia
      // a ogni errore, fino a [MaxRetryDelayMillis].
      dirty = true
      failures++
      if (pending == null) {
        val delay = (saveDelayMillis shl minOf(failures, 16)).coerceIn(1L, MaxRetryDelayMillis)
        pending = io.schedule({
          synchronized(lock) { pending = null }
          write()
        }, delay, TimeUnit.MILLISECONDS)
      }
    }
  }

  companion object {
    /** Le posizioni tenute: 300 file, una ventina di KB. A occhio, 25 novembre 2026. */
    const val DefaultMaxEntries: Int = 300

    /** L'attesa prima di scrivere sul disco dopo l'ultima modifica: 1,5 s. A occhio, 25 novembre 2026. */
    const val DefaultSaveDelayMillis: Long = 1_500L

    /** Ogni quanto si cercano le posizioni dei file spariti: un giorno. */
    const val PruneIntervalMillis: Long = 24L * 60 * 60 * 1000

    private const val Header = "#fluid-viewer-resume v1"

    /** R2 · L'attesa più lunga fra due tentativi di scrittura dopo un errore del disco: un minuto. A occhio, 26 novembre 2026. */
    private const val MaxRetryDelayMillis: Long = 60_000L
    private val stores = HashMap<String, FluidViewerResumeStore>()

    /**
     * Il negozio del processo, nel `noBackupFilesDir` dell'app. La prima volta scrive su disco quando l'app va in
     * background e, se è passato un giorno, pulisce le posizioni dei file spariti fuori dal thread principale.
     */
    fun get(context: Context): FluidViewerResumeStore {
      val app = context.applicationContext
      val file = File(File(app.noBackupFilesDir, "fluid-viewer"), "resume-v1.tsv")
      synchronized(stores) {
        stores[file.absolutePath]?.let { return it }
        val store = FluidViewerResumeStore(file)
        stores[file.absolutePath] = store
        app.registerComponentCallbacks(object : ComponentCallbacks2 {
          override fun onTrimMemory(level: Int) {
            if (level >= ComponentCallbacks2.TRIM_MEMORY_UI_HIDDEN) store.io.execute { store.write() }
          }
          override fun onConfigurationChanged(newConfig: Configuration) = Unit
          @Deprecated("Deprecated in Java")
          override fun onLowMemory() = Unit
        })
        store.io.execute {
          if (store.pruneDue) kotlinx.coroutines.runBlocking { store.prune(app) }
        }
        return store
      }
    }

    /** La chiave di serie di un file: il suo indirizzo. Lo stesso nome con un indirizzo diverso è un altro file. */
    fun keyOf(file: FluidViewerFile): String = file.uri.toString()

    /** La revisione di serie: peso e data, se si sanno; null se l'app non li dà (allora vale qualunque revisione). */
    fun revisionOf(file: FluidViewerFile): String? =
      if (file.sizeBytes == null && file.modifiedAtMillis == null) null
      else "${file.sizeBytes ?: ""}:${file.modifiedAtMillis ?: ""}"
  }
}

/**
 * Il file a [uri] esiste ancora? Vero, falso, o null se non si sa (un provider che non risponde, uno schema che non si
 * può chiedere). `file://`: il disco. `content://`: una riga dal provider (nessun provider, un permesso perso o un
 * documento sparito valgono falso). `android.resource://`: sempre vero.
 */
fun resumeTargetExists(context: Context, uri: Uri): Boolean? = when (uri.scheme) {
  "file" -> uri.path?.let { File(it).exists() } ?: false
  "android.resource" -> true
  "content" -> try {
    context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null).use { cursor ->
      cursor != null && cursor.moveToFirst()
    }
  } catch (_: SecurityException) {
    false
  } catch (_: FileNotFoundException) {
    false
  } catch (_: IllegalArgumentException) {
    false
  } catch (_: Exception) {
    null
  }
  else -> null
}

/**
 * Il posto della posizione di ogni file di [files] per [dev.antigravity.fluidengine.ui.fluid.viewer.FluidViewerResumeProvider]:
 * `FluidViewerResumeProvider(rememberFluidViewerResume(files)) { FluidFileViewer(count = files.count, content = files::content) }`.
 * Un file che il visore non riesce più a leggere perde la sua posizione. [key] e [revision] sono quelli di serie
 * ([FluidViewerResumeStore.keyOf], [FluidViewerResumeStore.revisionOf]); un'app con un id stabile dei suoi file (un
 * allegato, un documento del cloud) passa il suo.
 */
@Composable
fun rememberFluidViewerResume(
  files: FluidViewerFiles,
  store: FluidViewerResumeStore = FluidViewerResumeStore.get(LocalContext.current),
  key: (FluidViewerFile) -> String = FluidViewerResumeStore::keyOf,
  revision: (FluidViewerFile) -> String? = FluidViewerResumeStore::revisionOf,
): (Int) -> FluidViewerResumeSlot? {
  val currentKey by rememberUpdatedState(key)
  val currentRevision by rememberUpdatedState(revision)
  files.onFailure = { file, failure ->
    if (failure.failure == FluidViewerFailure.Unreadable) store.forget(currentKey(file))
  }
  return remember(files, store) {
    { index -> files.files.getOrNull(index)?.let { file -> store.slot(file, currentKey(file), currentRevision(file)) } }
  }
}

/**
 * Scrive [bytes] in [file] tutto o niente: prima un file accanto, poi lo spostamento atomico al suo posto (sostituisce
 * quello di prima, anche sulla JVM di Windows delle prove, dove `AtomicFile` non sostituisce un file che esiste).
 */
internal fun writeFileAtomically(file: File, bytes: ByteArray): Boolean {
  file.parentFile?.mkdirs()
  val partial = File(file.parentFile, file.name + ".parte")
  return try {
    partial.writeBytes(bytes)
    java.nio.file.Files.move(partial.toPath(), file.toPath(), java.nio.file.StandardCopyOption.REPLACE_EXISTING,
      java.nio.file.StandardCopyOption.ATOMIC_MOVE)
    true
  } catch (_: Exception) {
    partial.delete()
    false
  }
}

private fun String.escaped(): String = buildString(length) {
  for (c in this@escaped) when (c) {
    '\\' -> append("\\\\")
    '\t' -> append("\\t")
    '\n' -> append("\\n")
    '\r' -> append("\\r")
    else -> append(c)
  }
}

private fun String.unescaped(): String {
  if ('\\' !in this) return this
  return buildString(length) {
    var i = 0
    while (i < this@unescaped.length) {
      val c = this@unescaped[i]
      if (c == '\\' && i + 1 < this@unescaped.length) {
        when (this@unescaped[i + 1]) {
          't' -> append('\t')
          'n' -> append('\n')
          'r' -> append('\r')
          else -> append(this@unescaped[i + 1])
        }
        i += 2
      } else {
        append(c)
        i++
      }
    }
  }
}
