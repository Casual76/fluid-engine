package dev.antigravity.fluidengine.ui.fluid.share

import android.content.Context
import android.net.Uri
import androidx.core.content.FileProvider
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import java.io.File
import java.io.IOException
import java.util.UUID

/**
 * Il FileProvider dell'engine per cio' che l'app manda fuori (4.0, F8.10): le immagini da condividere, i PDF, le copie
 * preparate. Una sottoclasse propria, con l'autorita' `<applicationId>.fluid.share`, cosi' non litiga con il
 * `FileProvider` che l'app dichiara gia' (due provider con lo stesso nome di classe non si fondono nel manifest).
 * Non esportato: chi riceve legge solo l'Uri che gli si passa, con `FLAG_GRANT_READ_URI_PERMISSION`.
 */
class FluidShareFileProvider : FileProvider()

/**
 * La cartella delle uscite dell'engine: `cacheDir/fluid-share/<una cartella per uscita>/<nome>`. Una cartella per file
 * tiene il nome leggibile («Voto di matematica.png») senza che due uscite con lo stesso nome si sovrascrivano. Sta nella
 * cache: Android la puo' svuotare, e [clean] toglie le uscite vecchie (di serie un giorno).
 */
object FluidShareFiles {
  /** La cartella sotto `cacheDir`, la stessa di `res/xml/fluid_share_paths.xml`. */
  const val Folder: String = "fluid-share"

  /** L'autorita' del FileProvider dell'engine per questa app. */
  fun authority(context: Context): String = "${context.packageName}.fluid.share"

  /** La radice delle uscite. */
  fun root(context: Context): File = File(context.cacheDir, Folder)

  /** Un file nuovo e vuoto per un'uscita chiamata [name] (il nome si ripulisce dai caratteri che un file non ammette). */
  fun newFile(context: Context, name: String): File {
    val dir = File(root(context), UUID.randomUUID().toString().take(12))
    dir.mkdirs()
    return File(dir, fluidSafeFileName(name))
  }

  /**
   * L'Uri `content://` di un file sotto [root] (o un'altra cartella dichiarata nel provider dell'engine). Per un file
   * fuori lancia `IllegalArgumentException`: per un file qualsiasi dell'app c'e' [FluidShareItem.file], che lo copia qui
   * al momento della condivisione.
   */
  fun uriFor(context: Context, file: File): Uri =
    fluidShareUrisForTesting?.invoke(file) ?: FileProvider.getUriForFile(context, authority(context), file)

  /**
   * Vero se [file] sta sotto [root], dove il FileProvider dell'engine lo espone senza copia. Il confronto e' sui percorsi
   * canonici, cartella per cartella: `fluid-share2/` non e' dentro `fluid-share/`, e `..` non fa uscire.
   */
  fun isShareable(context: Context, file: File): Boolean = fluidIsUnder(root(context), file)

  /**
   * Il tetto di serie di una copia per la condivisione: 512 MB. Un file dell'app fuori da [root] si copia qui prima di
   * partire, e oltre il tetto il foglio dice perche' non parte invece di riempire la cache. A occhio (24 novembre 2026):
   * un video di qualche minuto in 4K ci sta, un archivio intero no.
   */
  const val DefaultCopyLimitBytes: Long = 512L * 1024 * 1024

  /**
   * Prepara [item] per la consegna: i suoi [FluidShareItem.localFiles] fuori da [root] si copiano qui (una cartella per
   * file, il nome resta), quelli gia' dentro si espongono cosi' come sono, e tutti diventano Uri `content://` del
   * provider dell'engine. Lavora su `Dispatchers.IO` e si puo' annullare (la copia a meta' si cancella).
   *
   * **Non lancia mai**: un file che non c'e', che non si legge, oltre [limitBytes] o senza spazio libero torna come
   * [FluidShareStaged.Failed], con il motivo per un messaggio umano. Il foglio lo fa da se'; chi manda con
   * [FluidShareIntents] senza il foglio chiama questo prima.
   */
  suspend fun stage(context: Context, item: FluidShareItem, limitBytes: Long = DefaultCopyLimitBytes): FluidShareStaged =
    withContext(Dispatchers.IO) {
      if (item.localFiles.isEmpty()) return@withContext FluidShareStaged.Ready(item)
      val root = root(context)
      root.mkdirs()
      when (val result = fluidStageFiles(root, item.localFiles, limitBytes, root.usableSpace) { newFile(context, it) }) {
        is FluidStageResult.Problem -> FluidShareStaged.Failed(result.problem, result.file.name, result.sizeBytes, limitBytes)
        is FluidStageResult.Done -> {
          val uris = try {
            result.files.map { uriFor(context, it) }
          } catch (_: IllegalArgumentException) {
            return@withContext FluidShareStaged.Failed(FluidShareFileProblem.Unreadable, item.localFiles.first().name, null, limitBytes)
          }
          FluidShareStaged.Ready(
            item.copy(uris = item.uris + uris, localFiles = emptyList(), sizeBytes = item.sizeBytes ?: result.totalBytes),
          )
        }
      }
    }

  /**
   * Solo per le prove: sul JVM di Windows il `FileProvider` non trova le sue radici (cerca «/» e i percorsi hanno «\»),
   * quindi le prove del foglio danno qui un Uri finto per ogni file. `null` torna al provider vero. Mai in un'app.
   */
  fun overrideUrisForTesting(uriFor: ((File) -> Uri)?) {
    fluidShareUrisForTesting = uriFor
  }

  /** Toglie le uscite piu' vecchie di [olderThanMillis]. Da chiamare fuori dal thread principale. */
  fun clean(context: Context, olderThanMillis: Long = 24L * 60 * 60 * 1000, now: Long = System.currentTimeMillis()) {
    root(context).listFiles()?.forEach { dir ->
      if (now - dir.lastModified() > olderThanMillis) dir.deleteRecursively()
    }
  }
}

/** Perche' un file dell'app non si puo' condividere (4.0, F8.10): il foglio lo dice con parole sue ([FluidShareLabels]). */
enum class FluidShareFileProblem {
  /** Il file non c'e' piu' (spostato, cancellato). */
  Missing,

  /** C'e' ma non si legge (una cartella, un permesso, un errore di lettura a meta' copia). */
  Unreadable,

  /** Oltre il tetto della copia ([FluidShareFiles.DefaultCopyLimitBytes], o quello di [FluidShareOptions]). */
  TooLarge,

  /** Non c'e' spazio libero per la copia. */
  NoSpace,
}

/** L'esito di [FluidShareFiles.stage]. */
sealed class FluidShareStaged {
  /** Pronto: [item] ha solo Uri `content://` e nessun file locale. */
  class Ready(val item: FluidShareItem) : FluidShareStaged()

  /** Non parte, per [problem]. [sizeBytes] e' il peso da copiare quando conta (oltre il tetto, senza spazio). */
  class Failed(
    val problem: FluidShareFileProblem,
    val fileName: String,
    val sizeBytes: Long?,
    val limitBytes: Long,
  ) : FluidShareStaged()
}

@Volatile
private var fluidShareUrisForTesting: ((File) -> Uri)? = null

/** L'esito della copia dei file, senza Android: la prova al JVM la legge cosi' com'e'. */
internal sealed class FluidStageResult {
  class Done(val files: List<File>, val totalBytes: Long) : FluidStageResult()
  class Problem(val problem: FluidShareFileProblem, val file: File, val sizeBytes: Long?) : FluidStageResult()
}

/** Il margine che una copia lascia libero sul disco, oltre al suo peso: 16 MB. A occhio, perche' il telefono non resti a zero. */
internal const val FluidStageSpareBytes: Long = 16L * 1024 * 1024

/**
 * Vero se [file] sta dentro [root] (non [root] stesso): percorsi canonici, confrontati cartella per cartella risalendo i
 * genitori, cosi' vale con «/» e con «\» e un nome che comincia come la radice (`fluid-share2`) non passa.
 */
internal fun fluidIsUnder(root: File, file: File): Boolean {
  val base = runCatching { root.canonicalFile }.getOrElse { root.absoluteFile }
  var parent = runCatching { file.canonicalFile }.getOrElse { file.absoluteFile }.parentFile
  while (parent != null) {
    if (parent == base) return true
    parent = parent.parentFile
  }
  return false
}

/**
 * Copia sotto [root] i [files] che stanno fuori ([newFile] da' il posto della copia per un nome), dopo aver controllato
 * che ci siano tutti, che si leggano, che il peso da copiare stia sotto [limitBytes] e che [freeBytes] basti (con
 * [FluidStageSpareBytes] di margine). Torna i file nello stesso ordine (l'originale se gia' dentro, la copia se fuori).
 * Un errore a meta' cancella le copie fatte; l'annullamento anche.
 */
internal suspend fun fluidStageFiles(
  root: File,
  files: List<File>,
  limitBytes: Long,
  freeBytes: Long,
  newFile: (String) -> File,
): FluidStageResult {
  for (file in files) {
    if (!file.exists()) return FluidStageResult.Problem(FluidShareFileProblem.Missing, file, null)
    if (!file.isFile || !file.canRead()) return FluidStageResult.Problem(FluidShareFileProblem.Unreadable, file, null)
  }
  val outside = files.filter { !fluidIsUnder(root, it) }
  val toCopy = outside.sumOf { it.length() }
  val total = files.sumOf { it.length() }
  if (toCopy > limitBytes) return FluidStageResult.Problem(FluidShareFileProblem.TooLarge, outside.maxBy { it.length() }, toCopy)
  if (outside.isNotEmpty() && toCopy + FluidStageSpareBytes > freeBytes) {
    return FluidStageResult.Problem(FluidShareFileProblem.NoSpace, outside.first(), toCopy)
  }
  val made = mutableListOf<File>()
  try {
    val staged = files.map { file ->
      if (file !in outside) return@map file
      val target = newFile(file.name)
      made += target
      file.inputStream().use { input ->
        target.outputStream().use { output ->
          val buffer = ByteArray(64 * 1024)
          while (true) {
            currentCoroutineContext().ensureActive()
            val read = input.read(buffer)
            if (read < 0) break
            output.write(buffer, 0, read)
          }
        }
      }
      target
    }
    return FluidStageResult.Done(staged, total)
  } catch (e: IOException) {
    made.forEach { it.delete() }
    return FluidStageResult.Problem(FluidShareFileProblem.Unreadable, files.first(), null)
  } catch (e: CancellationException) {
    made.forEach { it.delete() }
    throw e
  }
}

/** Un nome di file valido su Android e su un PC: niente separatori ne' caratteri riservati, al massimo 120 caratteri. */
internal fun fluidSafeFileName(name: String): String {
  val cleaned = name.trim().map { c -> if (c in "\\/:*?\"<>|" || c.code < 32) '_' else c }.joinToString("")
    .trim('.', ' ')
  val bounded = if (cleaned.length <= 120) cleaned else {
    val dot = cleaned.lastIndexOf('.')
    if (dot > 0 && cleaned.length - dot <= 10) cleaned.take(120 - (cleaned.length - dot)) + cleaned.substring(dot) else cleaned.take(120)
  }
  return bounded.ifEmpty { "file" }
}
