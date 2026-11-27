/*
 * engine-share-in (4.0, F8.10, `arrivi-da-altre-app`): cio' che arriva da un'altra app, letto in un modo solo.
 * Codice del Fluid Engine.
 */
package dev.antigravity.fluidengine.sharein

import android.content.ContentResolver
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.OpenableColumns
import androidx.compose.runtime.Immutable
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.Locale

/** Un file arrivato, prima della copia: l'Uri dell'altra app, il tipo, il nome e il peso se l'app li dice. */
@Immutable
class FluidIncomingFile(val uri: Uri, val mimeType: String?, val name: String?, val sizeBytes: Long?) {
  override fun toString(): String = "FluidIncomingFile($name, $mimeType, $sizeBytes B)"
}

/** Perche' qualcosa non e' arrivato o non si tiene. */
enum class FluidShareInProblem {
  /** L'intent non e' una condivisione (un'azione che l'engine non conosce). */
  NotAShare,

  /** Una condivisione vuota: niente testo, niente file. */
  Empty,

  /** Un tipo che l'app non accetta ([FluidShareInPolicy.acceptedTypes]). */
  TypeNotAccepted,

  /** Un file oltre il tetto ([FluidShareInPolicy.maxFileBytes] o [FluidShareInPolicy.maxTotalBytes]). */
  TooLarge,

  /** Oltre il numero massimo di file ([FluidShareInPolicy.maxFiles]). */
  TooMany,

  /** Il file non si legge: il permesso manca o e' scaduto, o l'altra app non risponde. */
  Unreadable,

  /** Un `file://` o un Uri dell'app stessa: mai letti, sono la strada per farle leggere i suoi file. */
  UnsafeUri,
}

/**
 * Cio' che e' arrivato, in una delle quattro forme in cui Android consegna (`SEND`, `SEND_MULTIPLE`, `PROCESS_TEXT`,
 * `VIEW`): un testo, un link, dei file, o niente che si possa tenere. Lo legge [FluidShareIn.read].
 */
sealed class FluidIncoming {
  /** Un testo; [selection] vero se viene dal menu del testo selezionato (`PROCESS_TEXT`), [readOnly] se non si puo' sostituire. */
  class Text(val text: String, val subject: String?, val selection: Boolean = false, val readOnly: Boolean = true) : FluidIncoming()

  /** Un link, col titolo che l'app di partenza gli da' (Chrome lo mette nell'oggetto). */
  class Link(val url: String, val title: String?) : FluidIncoming()

  /** Dei file, con il testo che li accompagna se c'e'. [viewed] vero per «apri con» (`VIEW`). */
  class Files(val files: List<FluidIncomingFile>, val text: String? = null, val viewed: Boolean = false) : FluidIncoming()

  /** Niente da tenere, e perche'. */
  class Unsupported(val problem: FluidShareInProblem, val detail: String = "") : FluidIncoming()
}

/**
 * Lettura e copia degli arrivi da altre app (4.0, F8.10). Il flusso di un'attivita' che riceve:
 *
 * 1. [read] l'intent (le quattro forme, i nomi e i pesi dei file);
 * 2. [copy] subito i file nella cartella dell'app, perche' il permesso di lettura vale solo finche' l'attivita' vive;
 * 3. il foglio ([FluidShareInSheet]) mostra l'anteprima e la destinazione; l'app salva davvero.
 *
 * Lo stato che fa tutto questo e' [rememberFluidShareInState].
 */
object FluidShareIn {
  /** Le azioni che l'engine sa leggere. */
  val Actions: Set<String> = setOf(Intent.ACTION_SEND, Intent.ACTION_SEND_MULTIPLE, Intent.ACTION_PROCESS_TEXT, Intent.ACTION_VIEW)

  /**
   * Legge [intent]. Chiede al `ContentResolver` dell'altra app il tipo, il nome e il peso di ogni file: va su
   * `Dispatchers.IO`. Dalla [policy] prende solo le autorita' fidate dell'app stessa; i limiti li applica [copy].
   */
  suspend fun read(context: Context, intent: Intent?, policy: FluidShareInPolicy = FluidShareInPolicy()): FluidIncoming = withContext(Dispatchers.IO) {
    val unsafe = fluidUnsafeUri(context, policy)
    val shape = fluidIncomingShape(intent, context.packageName, policy.trustedAuthorities, unsafe)
    if (shape !is FluidIncoming.Files) return@withContext shape
    val resolver = context.contentResolver
    // Un Uri insicuro fra quelli buoni resta nella lista per dire che non e' arrivato, ma non si chiede niente a nessuno.
    FluidIncoming.Files(shape.files.map { if (unsafe(it.uri)) it else describe(resolver, it, intent?.type) }, shape.text, shape.viewed)
  }

  /** Il tipo, il nome e il peso di [file] chiesti all'app che lo offre; quello che non dice resta nullo. */
  internal fun describe(resolver: ContentResolver, file: FluidIncomingFile, fallbackType: String?): FluidIncomingFile {
    val type = runCatching { resolver.getType(file.uri) }.getOrNull() ?: file.mimeType ?: fallbackType?.takeIf { '*' !in it }
    var name: String? = null
    var size: Long? = null
    runCatching {
      resolver.query(file.uri, arrayOf(OpenableColumns.DISPLAY_NAME, OpenableColumns.SIZE), null, null, null)?.use { c ->
        if (c.moveToFirst()) {
          val n = c.getColumnIndex(OpenableColumns.DISPLAY_NAME)
          val s = c.getColumnIndex(OpenableColumns.SIZE)
          if (n >= 0 && !c.isNull(n)) name = c.getString(n)
          if (s >= 0 && !c.isNull(s)) size = c.getLong(s).takeIf { it >= 0 }
        }
      }
    }
    return FluidIncomingFile(file.uri, type, name ?: file.uri.lastPathSegment, size)
  }
}

/**
 * La forma di [intent] senza chiedere niente a nessuno: l'azione, gli extra e il `ClipData`. [ownPackage] serve a
 * rifiutare gli Uri dell'app stessa.
 */
internal fun fluidIncomingShape(
  intent: Intent?,
  ownPackage: String,
  trusted: Set<String> = emptySet(),
  unsafe: (Uri) -> Boolean = { it.scheme?.lowercase(Locale.ROOT) != "content" || fluidIsOwnUri(it, ownPackage, trusted) },
): FluidIncoming {
  if (intent == null || intent.action !in FluidShareIn.Actions) {
    return FluidIncoming.Unsupported(FluidShareInProblem.NotAShare, intent?.action.orEmpty())
  }
  val type = intent.type
  return when (intent.action) {
    Intent.ACTION_PROCESS_TEXT -> {
      val text = intent.getCharSequenceExtra(Intent.EXTRA_PROCESS_TEXT)?.toString()
      if (text.isNullOrBlank()) FluidIncoming.Unsupported(FluidShareInProblem.Empty)
      else FluidIncoming.Text(text, subject = null, selection = true,
        readOnly = intent.getBooleanExtra(Intent.EXTRA_PROCESS_TEXT_READONLY, false))
    }
    Intent.ACTION_VIEW -> {
      val data = intent.data ?: return FluidIncoming.Unsupported(FluidShareInProblem.Empty)
      when (data.scheme?.lowercase(Locale.ROOT)) {
        "http", "https" -> FluidIncoming.Link(data.toString(), null)
        "content" -> if (unsafe(data)) FluidIncoming.Unsupported(FluidShareInProblem.UnsafeUri)
        else FluidIncoming.Files(listOf(FluidIncomingFile(data, type, null, null)), viewed = true)
        else -> FluidIncoming.Unsupported(FluidShareInProblem.UnsafeUri, data.scheme.orEmpty())
      }
    }
    else -> {
      val uris = fluidStreamUris(intent)
      val text = intent.getCharSequenceExtra(Intent.EXTRA_TEXT)?.toString()?.takeIf { it.isNotBlank() }
      val subject = intent.getStringExtra(Intent.EXTRA_SUBJECT)?.takeIf { it.isNotBlank() }
      when {
        uris.isNotEmpty() -> {
          val refused = uris.filter(unsafe)
          // R2 · Tutti insicuri: niente da tenere. Alcuni: restano nella lista, e la copia li rifiuta col motivo
          // (`UnsafeUri`), cosi' la persona legge quali non sono arrivati invece di vederli sparire.
          if (refused.size == uris.size) FluidIncoming.Unsupported(FluidShareInProblem.UnsafeUri, refused.first().scheme.orEmpty())
          else FluidIncoming.Files(uris.map { uri ->
            if (uri in refused) FluidIncomingFile(uri, null, uri.lastPathSegment, null)
            else FluidIncomingFile(uri, type?.takeIf { t -> '*' !in t }, null, null)
          }, text)
        }
        text != null -> fluidLinkOf(text)?.let { FluidIncoming.Link(it, subject) } ?: FluidIncoming.Text(text, subject)
        else -> FluidIncoming.Unsupported(FluidShareInProblem.Empty)
      }
    }
  }
}

/** Gli Uri di `EXTRA_STREAM` (uno o una lista) e del `ClipData`, senza doppioni, nell'ordine. */
internal fun fluidStreamUris(intent: Intent): List<Uri> {
  val out = LinkedHashSet<Uri>()
  // R2 · Un intent di un'altra app puo' mettere in EXTRA_STREAM un oggetto che non e' un Uri: prima di Android 13 le
  // letture non controllano il tipo, e nemmeno quelle nuove controllano gli elementi di una lista non ancora letta da un
  // Parcel. Si leggono come oggetti qualsiasi e si tengono solo gli Uri: un tipo sbagliato non fa cadere la ricezione.
  if (intent.action == Intent.ACTION_SEND_MULTIPLE) {
    // `List<*>`: gli elementi si guardano come oggetti qualsiasi, senza il cast che lancerebbe.
    val list: List<*>? = runCatching {
      if (Build.VERSION.SDK_INT >= 33) intent.getParcelableArrayListExtra(Intent.EXTRA_STREAM, android.os.Parcelable::class.java)
      else @Suppress("DEPRECATION") intent.getParcelableArrayListExtra<android.os.Parcelable>(Intent.EXTRA_STREAM)
    }.getOrNull()
    list?.forEach { item -> (item as? Uri)?.let(out::add) }
  } else {
    val one: Any? = runCatching {
      if (Build.VERSION.SDK_INT >= 33) intent.getParcelableExtra(Intent.EXTRA_STREAM, android.os.Parcelable::class.java)
      else @Suppress("DEPRECATION") intent.getParcelableExtra<android.os.Parcelable>(Intent.EXTRA_STREAM)
    }.getOrNull()
    (one as? Uri)?.let(out::add)
  }
  runCatching { intent.clipData?.let { clip -> for (i in 0 until clip.itemCount) clip.getItemAt(i).uri?.let(out::add) } }
  return out.toList()
}

/** Il link di un testo che e' solo un link (anche con il titolo davanti, come lo manda Chrome: «Titolo https://…»). */
internal fun fluidLinkOf(text: String): String? {
  val trimmed = text.trim()
  val last = trimmed.substringAfterLast(' ').substringAfterLast('\n')
  val lower = last.lowercase(Locale.ROOT)
  if (!(lower.startsWith("https://") || lower.startsWith("http://")) || last.length < 10) return null
  // Un link in fondo a un testo lungo e' un testo con un link, non un link.
  val rest = trimmed.removeSuffix(last).trim()
  return if (rest.length <= 120 && '\n' !in rest) last else null
}

/**
 * Vero se [uri] e' servito da questa app e non da un'autorita' dichiarata fidata ([trusted]): un'altra app non deve
 * poterle far copiare i suoi stessi file privati.
 */
internal fun fluidIsOwnUri(uri: Uri, ownPackage: String, trusted: Set<String> = emptySet()): Boolean {
  val authority = uri.authority?.lowercase(Locale.ROOT) ?: return false
  if (trusted.any { it.lowercase(Locale.ROOT) == authority }) return false
  val own = ownPackage.lowercase(Locale.ROOT)
  return authority == own || authority.startsWith("$own.")
}

/**
 * Gli Uri che non si leggono mai, per questa app: non `content://`, o serviti da lei e non dichiarati fidati. «Serviti da
 * lei» lo dice il `PackageManager` (R2: un provider dell'app con un'autorita' che non comincia col nome del pacchetto non
 * era riconosciuto), col prefisso del pacchetto come ripiego.
 */
internal fun fluidUnsafeUri(context: Context, policy: FluidShareInPolicy): (Uri) -> Boolean {
  val own = context.packageName
  val trusted = policy.trustedAuthorities.map { it.lowercase(Locale.ROOT) }.toSet()
  val pm = context.packageManager
  return { uri ->
    val authority = uri.authority?.lowercase(Locale.ROOT)
    when {
      uri.scheme?.lowercase(Locale.ROOT) != "content" -> true
      authority == null -> true
      authority in trusted -> false
      fluidIsOwnUri(uri, own, policy.trustedAuthorities) -> true
      else -> runCatching {
        // Un'autorita' puo' portare piu' nomi separati da «;»: il PackageManager la cerca per nome.
        @Suppress("DEPRECATION")
        pm.resolveContentProvider(uri.authority!!, 0)?.packageName == own
      }.getOrDefault(false)
    }
  }
}
