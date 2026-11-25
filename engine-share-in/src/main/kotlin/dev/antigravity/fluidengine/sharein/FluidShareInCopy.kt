package dev.antigravity.fluidengine.sharein

import android.content.ContentResolver
import android.content.Context
import androidx.compose.runtime.Immutable
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import java.io.File
import java.io.IOException
import java.util.Locale
import java.util.UUID
import kotlin.coroutines.coroutineContext

/**
 * Cosa l'app accetta da fuori: i tipi, i tetti di peso e di numero (4.0, F8.10). I tetti di serie sono quelli di una
 * nota o di una mail (100 MB per file, 250 MB in tutto, 20 file); un'app che salva video sul NAS li alza.
 *
 * @param acceptedTypes i tipi MIME, anche con `*` («image/ *», «application/pdf», «* / *» senza spazi).
 * @param acceptText vero se l'app tiene anche un testo o un link senza file.
 * @param trustedAuthorities le autorita' dell'app stessa da cui si puo' ricevere (di serie nessuna: un Uri dell'app
 *   che arriva da fuori e' la strada per farle copiare i suoi file privati).
 */
@Immutable
data class FluidShareInPolicy(
  val acceptedTypes: List<String> = listOf("*/*"),
  val maxFileBytes: Long = 100L * 1024 * 1024,
  val maxTotalBytes: Long = 250L * 1024 * 1024,
  val maxFiles: Int = 20,
  val acceptText: Boolean = true,
  val trustedAuthorities: Set<String> = emptySet(),
) {
  init {
    require(maxFileBytes > 0 && maxTotalBytes > 0 && maxFiles > 0)
  }

  /** Vero se il tipo [mimeType] e' fra quelli accettati; un tipo ignoto passa solo con «* / *». */
  fun accepts(mimeType: String?): Boolean {
    val type = mimeType?.substringBefore(';')?.trim()?.lowercase(Locale.ROOT)
    return acceptedTypes.any { pattern ->
      val p = pattern.trim().lowercase(Locale.ROOT)
      when {
        p == "*/*" -> true
        type == null -> false
        p.endsWith("/*") -> type.substringBefore('/') == p.substringBefore('/')
        else -> type == p
      }
    }
  }
}

/** A che punto e' la copia: i byte copiati, il totale se noto, il file in corso. */
@Immutable
class FluidShareInProgress(val copiedBytes: Long, val totalBytes: Long?, val fileIndex: Int, val fileCount: Int) {
  /** La frazione fatta, se il totale e' noto. */
  val fraction: Float? get() = totalBytes?.takeIf { it > 0 }?.let { (copiedBytes.toDouble() / it).toFloat().coerceIn(0f, 1f) }
}

/** Un file dopo la copia: la copia locale, o il motivo per cui non c'e'. */
@Immutable
class FluidCopiedFile(val source: FluidIncomingFile, val file: File?, val problem: FluidShareInProblem?) {
  val ok: Boolean get() = file != null && problem == null
  override fun toString(): String = "FluidCopiedFile(${source.name}, ${file?.length()} B, $problem)"
}

/** Le copie degli arrivi stanno qui: `cacheDir/fluid-share-in/<una cartella per arrivo>`. */
object FluidShareInFiles {
  const val Folder: String = "fluid-share-in"

  fun root(context: Context): File = File(context.cacheDir, Folder)

  fun newSession(context: Context): File = File(root(context), UUID.randomUUID().toString().take(12)).apply { mkdirs() }

  /** Toglie gli arrivi piu' vecchi di [olderThanMillis] che l'app non ha spostato. Fuori dal thread principale. */
  fun clean(context: Context, olderThanMillis: Long = 24L * 60 * 60 * 1000, now: Long = System.currentTimeMillis()) {
    root(context).listFiles()?.forEach { if (now - it.lastModified() > olderThanMillis) it.deleteRecursively() }
  }
}

/**
 * Copia [files] in [into], uno alla volta, su `Dispatchers.IO` (mai sul thread principale).
 *
 * - **Prima** scarta cio' che la [policy] non accetta: il tipo, il numero, il peso dichiarato oltre il tetto.
 * - **Durante** conta i byte: un file che dice di pesare poco (o non lo dice) e supera il tetto si ferma li', e la
 *   copia a meta' si cancella. Lo stesso per il totale.
 * - Un file che non si legge (permesso scaduto, app che non risponde) diventa [FluidShareInProblem.Unreadable]; gli
 *   altri continuano.
 * - [onProgress] arriva dal thread della copia, al piu' ogni 256 KB e alla fine di ogni file.
 */
suspend fun FluidShareIn.copy(
  context: Context,
  files: List<FluidIncomingFile>,
  policy: FluidShareInPolicy,
  into: File = FluidShareInFiles.newSession(context),
  onProgress: (FluidShareInProgress) -> Unit = {},
): List<FluidCopiedFile> = withContext(Dispatchers.IO) {
  fluidCopyIncoming(context.contentResolver, files, policy, into, onProgress)
}

internal const val FluidShareInProgressStep: Long = 256L * 1024

internal suspend fun fluidCopyIncoming(
  resolver: ContentResolver,
  files: List<FluidIncomingFile>,
  policy: FluidShareInPolicy,
  into: File,
  onProgress: (FluidShareInProgress) -> Unit,
): List<FluidCopiedFile> {
  into.mkdirs()
  val declaredTotal = files.take(policy.maxFiles).mapNotNull { it.sizeBytes }.takeIf { it.size == minOf(files.size, policy.maxFiles) }?.sum()
  var copied = 0L
  val used = HashSet<String>()
  return files.mapIndexed { index, file ->
    coroutineContext.ensureActive()
    val problem = when {
      index >= policy.maxFiles -> FluidShareInProblem.TooMany
      !policy.accepts(file.mimeType) -> FluidShareInProblem.TypeNotAccepted
      (file.sizeBytes ?: 0) > policy.maxFileBytes -> FluidShareInProblem.TooLarge
      copied + (file.sizeBytes ?: 0) > policy.maxTotalBytes -> FluidShareInProblem.TooLarge
      else -> null
    }
    if (problem != null) return@mapIndexed FluidCopiedFile(file, null, problem)
    val target = File(into, fluidUniqueName(fluidIncomingName(file, index), used))
    var written = 0L
    var lastReport = 0L
    val result = try {
      val input = resolver.openInputStream(file.uri) ?: throw IOException("Nessun flusso")
      input.use { stream ->
        target.outputStream().use { out ->
          val buffer = ByteArray(64 * 1024)
          while (true) {
            coroutineContext.ensureActive()
            val n = stream.read(buffer)
            if (n < 0) break
            written += n
            if (written > policy.maxFileBytes || copied + written > policy.maxTotalBytes) throw FluidTooLarge()
            out.write(buffer, 0, n)
            if (written - lastReport >= FluidShareInProgressStep) {
              lastReport = written
              onProgress(FluidShareInProgress(copied + written, declaredTotal, index, files.size))
            }
          }
        }
      }
      null
    } catch (_: FluidTooLarge) {
      FluidShareInProblem.TooLarge
    } catch (_: SecurityException) {
      FluidShareInProblem.Unreadable
    } catch (_: IOException) {
      FluidShareInProblem.Unreadable
    } catch (_: IllegalArgumentException) {
      FluidShareInProblem.Unreadable
    } catch (t: Throwable) {
      target.delete()
      throw t
    }
    if (result != null) {
      target.delete()
      FluidCopiedFile(file, null, result)
    } else {
      copied += written
      onProgress(FluidShareInProgress(copied, declaredTotal, index, files.size))
      FluidCopiedFile(file, target, null)
    }
  }
}

private class FluidTooLarge : RuntimeException()

/** Il nome della copia: quello dell'altra app ripulito, o «arrivo-N» con l'estensione del tipo. */
internal fun fluidIncomingName(file: FluidIncomingFile, index: Int): String {
  val raw = file.name?.substringAfterLast('/')?.substringAfterLast('\\')?.trim().orEmpty()
  val clean = raw.map { c -> if (c in "\\/:*?\"<>|" || c.code < 32) '_' else c }.joinToString("").trim('.', ' ').take(120)
  if (clean.isNotEmpty()) return clean
  val ext = android.webkit.MimeTypeMap.getSingleton().getExtensionFromMimeType(file.mimeType ?: "")?.let { ".$it" }.orEmpty()
  return "arrivo-${index + 1}$ext"
}

/** [name], o «nome (2).ext» se c'e' gia'. */
internal fun fluidUniqueName(name: String, used: MutableSet<String>): String {
  if (used.add(name.lowercase(Locale.ROOT))) return name
  val dot = name.lastIndexOf('.').takeIf { it > 0 } ?: name.length
  var n = 2
  while (true) {
    val candidate = name.substring(0, dot) + " ($n)" + name.substring(dot)
    if (used.add(candidate.lowercase(Locale.ROOT))) return candidate
    n++
  }
}
