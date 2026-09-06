package dev.antigravity.fluidengine.ai.bridge

import android.content.ContentProvider
import android.content.ContentValues
import android.content.Intent
import android.database.Cursor
import android.net.Uri
import android.os.Bundle
import androidx.core.content.FileProvider
import dev.antigravity.fluidengine.ai.provider.ContentPart
import dev.antigravity.fluidengine.ai.tools.AiTool
import dev.antigravity.fluidengine.ai.tools.ToolOutput
import dev.antigravity.fluidengine.ai.tools.ToolRegistry
import java.io.File
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject

/** Chi sta chiamando e come: il pacchetto, la lingua, se la conferma e' gia' stata data. */
data class RemoteCall(val callingPackage: String?, val language: String, val confirmed: Boolean, val requestId: String)

/** L'ospite e' pronto a rispondere? (loggato, con dati, con il motore avviato). */
data class ReadyState(val ready: Boolean, val reason: String? = null)

/**
 * L'ospite: un `ContentProvider` che espone i tool dell'app a un assistente esterno con lo stesso
 * firmatario. L'app dichiara il provider nel manifest con il permesso [BridgeProtocol.PERMISSION],
 * l'autorita' `<applicationId>.ai.tools` e il meta-data [BridgeProtocol.META_TOOL_HOST], e in una
 * sottoclasse da' il registry, il contesto dei tool e le parole di se stessa. Il resto — il
 * protocollo, i lavori lunghi, le parti consegnate come file, i timeout — sta qui.
 *
 * Il cancello delle conferme e' del **cliente**: qui un tool con `needsConfirmation` gira solo se la
 * chiamata dice `confirmed = true`, e il contesto che l'app costruisce deve essere pre-confermato
 * (il suo gate risponde si' senza chiedere). Difesa in profondita': senza `confirmed` il tool non
 * parte, qualunque cosa dica il cliente.
 */
abstract class AiToolHostProvider<C> : ContentProvider() {

  /** Il registry dell'app: lo stesso del suo assistente interno. */
  abstract fun registry(): ToolRegistry<C>

  /** Il contesto con cui girano i tool per questa chiamata, con il gate gia' confermato. */
  abstract suspend fun context(call: RemoteCall): C

  /** Il prefisso dei nomi nel registry del cliente (`cv`, `meteo`, `bus`, `convert`, `store`). */
  abstract fun domain(): String

  open fun appLabel(): String = context?.applicationInfo?.loadLabel(context!!.packageManager)?.toString() ?: domain()

  /** Una riga per il router del cliente: cosa sa fare quest'app, in parole. */
  open fun routerHint(): String? = null

  /** Parole da riconoscere a voce (nomi di fermate, materie, luoghi salvati). */
  open fun vocabulary(): List<String> = emptyList()

  open fun ready(): ReadyState = ReadyState(true)

  /** Quanto una singola `call` puo' durare prima di rispondere "in corso" o "timeout". */
  open val callTimeoutMillis: Long = 25_000L

  /**
   * L'autorita' del `FileProvider` con cui consegnare le parti (screenshot, PDF) al cliente; null
   * = le parti si scartano e resta il testo. La cartella e' `cacheDir/ai-bridge/`: va dichiarata nei
   * `file_paths.xml` del FileProvider (`<cache-path name="ai-bridge" path="ai-bridge/"/>`).
   */
  open fun partsAuthority(): String? = null

  private val jobs = BridgeJobs()

  final override fun onCreate(): Boolean = true

  final override fun call(method: String, arg: String?, extras: Bundle?): Bundle {
    val request = BridgeProtocol.parse(extras?.getString(BridgeProtocol.KEY_JSON))
    val response: JsonObject = try {
      when (method) {
        BridgeProtocol.METHOD_CATALOG -> catalog()
        BridgeProtocol.METHOD_READY -> ready().let { BridgeProtocol.readyResponse(it.ready, it.reason) }
        BridgeProtocol.METHOD_DESCRIBE -> describe(request)
        BridgeProtocol.METHOD_RUN -> run(request)
        BridgeProtocol.METHOD_STATUS -> status(request)
        BridgeProtocol.METHOD_CANCEL -> cancel(request)
        else -> BridgeProtocol.errorResponse("metodo sconosciuto: $method")
      }
    } catch (e: Throwable) {
      BridgeProtocol.errorResponse("${appLabel()} non ha risposto: ${e.message ?: e::class.simpleName}")
    }
    return Bundle().apply { putString(BridgeProtocol.KEY_JSON, response.toString()) }
  }

  private fun catalog(): JsonObject {
    val ctx = context!!
    val version = runCatching { ctx.packageManager.getPackageInfo(ctx.packageName, 0).versionName }.getOrNull() ?: ""
    return BridgeProtocol.catalogJson(registry(), ctx.packageName, domain(), appLabel(), version, routerHint(), vocabulary())
  }

  private fun describe(request: JsonObject?): JsonObject {
    val name = request?.get("name")?.let { (it as? kotlinx.serialization.json.JsonPrimitive)?.content } ?: return BridgeProtocol.errorResponse("manca il nome")
    val tool = registry().find(name) ?: return BridgeProtocol.errorResponse("tool sconosciuto: $name")
    val args = request["args"] as? JsonObject ?: buildJsonObject { }
    val language = (request["lang"] as? kotlinx.serialization.json.JsonPrimitive)?.content ?: "it"
    val text = runBlocking(Dispatchers.IO) {
      withTimeoutOrNull(callTimeoutMillis) {
        val ctx = context(RemoteCall(callingPackage, language, confirmed = false, requestId = UUID.randomUUID().toString()))
        tool.describe(args, ctx)
      }
    }
    return BridgeProtocol.describeResponse(text?.title, text?.detail)
  }

  private fun run(request: JsonObject?): JsonObject {
    val name = request?.get("name")?.let { (it as? kotlinx.serialization.json.JsonPrimitive)?.content } ?: return BridgeProtocol.errorResponse("manca il nome")
    val tool = registry().find(name) ?: return BridgeProtocol.errorResponse("tool sconosciuto: $name")
    val args = request["args"] as? JsonObject ?: buildJsonObject { }
    val confirmed = (request["confirmed"] as? kotlinx.serialization.json.JsonPrimitive)?.content == "true"
    val language = (request["lang"] as? kotlinx.serialization.json.JsonPrimitive)?.content ?: "it"
    val requestId = (request["requestId"] as? kotlinx.serialization.json.JsonPrimitive)?.content ?: UUID.randomUUID().toString()
    if (tool.needsConfirmation && !confirmed) return BridgeProtocol.errorResponse("conferma richiesta: il chiamante deve chiedere all'utente prima di eseguire $name")
    val state = ready()
    if (!state.ready) return BridgeProtocol.errorResponse(state.reason ?: "${appLabel()} non e' pronta: apri l'app e riprova")
    val call = RemoteCall(callingPackage, language, confirmed, requestId)
    if (tool.longRunning) {
      val jobId = jobs.start { execute(tool, args, call) }
      return BridgeProtocol.jobResponse(jobId)
    }
    val output = runBlocking(Dispatchers.IO) { withTimeoutOrNull(callTimeoutMillis) { execute(tool, args, call) } }
      ?: return BridgeProtocol.errorResponse("$name non ha risposto entro ${callTimeoutMillis / 1000} secondi")
    return BridgeProtocol.resultResponse(output.text, output.escalate, export(requestId, output.parts))
  }

  private suspend fun execute(tool: AiTool<C>, args: JsonObject, call: RemoteCall): ToolOutput {
    val ctx = context(call)
    return try {
      tool.run(args, ctx)
    } catch (e: CancellationException) {
      throw e
    } catch (e: Throwable) {
      ToolOutput.error("${tool.name} ha fallito: ${e.message ?: e::class.simpleName}")
    }
  }

  private fun status(request: JsonObject?): JsonObject {
    val jobId = (request?.get("jobId") as? kotlinx.serialization.json.JsonPrimitive)?.content ?: return BridgeProtocol.errorResponse("manca jobId")
    val job = jobs[jobId] ?: return BridgeProtocol.statusResponse(BridgeProtocol.STATE_UNKNOWN, "lavoro sconosciuto (l'app e' stata riavviata?)", null, null, false, emptyList())
    val output = job.output
    return when {
      job.failure != null -> BridgeProtocol.statusResponse(BridgeProtocol.STATE_FAILED, job.failure, null, null, false, emptyList())
      output != null -> BridgeProtocol.statusResponse(BridgeProtocol.STATE_DONE, null, 1.0, output.text, output.escalate, export(jobId, output.parts))
      else -> BridgeProtocol.statusResponse(BridgeProtocol.STATE_RUNNING, job.detail, job.progress, null, false, emptyList())
    }
  }

  private fun cancel(request: JsonObject?): JsonObject {
    val jobId = (request?.get("jobId") as? kotlinx.serialization.json.JsonPrimitive)?.content ?: return BridgeProtocol.errorResponse("manca jobId")
    jobs.cancel(jobId)
    return BridgeProtocol.statusResponse(BridgeProtocol.STATE_FAILED, "annullato", null, null, false, emptyList())
  }

  /** Le parti diventano file nella cache e URI con grant di lettura al chiamante. */
  private fun export(requestId: String, parts: List<ContentPart>): List<RemotePart> {
    val authority = partsAuthority() ?: return emptyList()
    val ctx = context ?: return emptyList()
    val dir = File(ctx.cacheDir, "ai-bridge/$requestId").apply { mkdirs() }
    return parts.mapIndexedNotNull { index, part ->
      val (bytes, mime, name, kind) = when (part) {
        is ContentPart.Image -> Quad(part.bytes, part.mime, "immagine-$index.${if (part.mime.contains("png")) "png" else "jpg"}", RemotePart.KIND_IMAGE)
        is ContentPart.Document -> Quad(part.bytes, part.mime, part.name, RemotePart.KIND_DOCUMENT)
        is ContentPart.Text -> return@mapIndexedNotNull null
      }
      val file = File(dir, name.replace(Regex("[^A-Za-z0-9._-]"), "_"))
      runCatching { file.writeBytes(bytes) }.getOrElse { return@mapIndexedNotNull null }
      val uri = runCatching { FileProvider.getUriForFile(ctx, authority, file) }.getOrNull() ?: return@mapIndexedNotNull null
      callingPackage?.let { ctx.grantUriPermission(it, uri, Intent.FLAG_GRANT_READ_URI_PERMISSION) }
      RemotePart(kind, uri.toString(), mime, name)
    }
  }

  private data class Quad(val bytes: ByteArray, val mime: String, val name: String, val kind: String)

  // Un provider "solo call": le query non esistono.
  final override fun query(uri: Uri, projection: Array<out String>?, selection: String?, selectionArgs: Array<out String>?, sortOrder: String?): Cursor? = null
  final override fun getType(uri: Uri): String? = null
  final override fun insert(uri: Uri, values: ContentValues?): Uri? = null
  final override fun delete(uri: Uri, selection: String?, selectionArgs: Array<out String>?): Int = 0
  final override fun update(uri: Uri, values: ContentValues?, selection: String?, selectionArgs: Array<out String>?): Int = 0
}

/**
 * I lavori lunghi di un ospite (un'installazione, una conversione): partono in un proprio scope,
 * il cliente li segue con `status`. Vivono quanto il processo dell'ospite; un lavoro finito resta
 * consultabile dieci minuti, poi si dimentica.
 */
class BridgeJobs {
  class Entry(val job: Job) {
    @Volatile var output: ToolOutput? = null
    @Volatile var failure: String? = null
    @Volatile var detail: String? = null
    @Volatile var progress: Double? = null
    @Volatile var finishedAt: Long = 0L
  }

  private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
  private val entries = ConcurrentHashMap<String, Entry>()

  fun start(block: suspend () -> ToolOutput): String {
    prune()
    val id = UUID.randomUUID().toString()
    lateinit var entry: Entry
    val job = scope.launch {
      try {
        entry.output = block()
      } catch (e: CancellationException) {
        entry.failure = "annullato"
      } catch (e: Throwable) {
        entry.failure = e.message ?: e::class.simpleName
      } finally {
        entry.finishedAt = System.currentTimeMillis()
      }
    }
    entry = Entry(job)
    entries[id] = entry
    return id
  }

  operator fun get(id: String): Entry? = entries[id]

  fun cancel(id: String) {
    entries[id]?.job?.cancel()
  }

  /** Un tool che conosce il proprio jobId puo' raccontare a che punto e'. */
  fun report(id: String, detail: String?, progress: Double?) {
    entries[id]?.let { it.detail = detail; it.progress = progress }
  }

  private fun prune() {
    val now = System.currentTimeMillis()
    entries.entries.removeIf { (_, e) -> e.finishedAt > 0L && now - e.finishedAt > KEEP_MILLIS }
  }

  private companion object {
    const val KEEP_MILLIS = 10 * 60_000L
  }
}
