package dev.antigravity.fluidengine.ai.bridge

import android.content.Context
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
import android.os.DeadObjectException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.JsonObject

/** Cosa il cliente puo' dire di un'app ospite. */
enum class RemoteAvailability { INSTALLED_OK, NEEDS_UPDATE, NOT_INSTALLED, NO_PERMISSION, NOT_READY }

/**
 * Il cliente: trova le app che espongono tool ([discover]) e le chiama. Ogni chiamata passa da un
 * `ContentProviderClient` *unstable*, cosi' un ospite che muore a meta' non uccide il cliente: si
 * riceve un'eccezione e si risponde con un errore. Il cliente dichiara nel manifest un
 * `<queries>` con `QUERY_ALL_PACKAGES` o con le autorita' note, altrimenti Android nasconde i
 * provider delle altre app.
 */
class AiToolClient(private val context: Context) : BridgeCalls {

  /** Le app installate che espongono i tool: quelle con il meta-data sul provider. */
  fun discover(): List<RemoteHost> {
    val pm = context.packageManager
    val providers = runCatching { pm.queryContentProviders(null, 0, PackageManager.GET_META_DATA) }.getOrNull().orEmpty()
    return providers
      .filter { it.metaData?.getInt(BridgeProtocol.META_TOOL_HOST, 0) == 1 }
      .map { info -> RemoteHost(info.packageName, info.authority, info.loadLabel(pm).toString()) }
      .distinctBy { it.authority }
  }

  fun availability(host: RemoteHost): RemoteAvailability {
    val installed = runCatching { context.packageManager.getPackageInfo(host.packageName, 0); true }.getOrDefault(false)
    if (!installed) return RemoteAvailability.NOT_INSTALLED
    return when (val result = callRaw(host.authority, BridgeProtocol.METHOD_READY, null)) {
      is CallResult.Denied -> RemoteAvailability.NO_PERMISSION
      is CallResult.Missing -> RemoteAvailability.NEEDS_UPDATE
      is CallResult.Failed -> RemoteAvailability.NOT_READY
      is CallResult.Ok -> {
        val json = BridgeProtocol.parse(result.bundle.getString(BridgeProtocol.KEY_JSON))
        if ((json?.get("ready") as? kotlinx.serialization.json.JsonPrimitive)?.content == "true") RemoteAvailability.INSTALLED_OK else RemoteAvailability.NOT_READY
      }
    }
  }

  /** Il catalogo di un ospite; null se non risponde, non ha il permesso, o parla un protocollo piu' nuovo. */
  suspend fun catalog(host: RemoteHost): RemoteCatalog? = withContext(Dispatchers.IO) {
    val result = callRaw(host.authority, BridgeProtocol.METHOD_CATALOG, null) as? CallResult.Ok ?: return@withContext null
    val catalog = BridgeProtocol.catalogOf(BridgeProtocol.parse(result.bundle.getString(BridgeProtocol.KEY_JSON)), host.authority, host.packageName) ?: return@withContext null
    if (catalog.protocol > BridgeProtocol.PROTOCOL) null else catalog
  }

  override suspend fun describe(authority: String, name: String, args: JsonObject, language: String): Pair<String?, String?>? = withContext(Dispatchers.IO) {
    val result = callJson(authority, BridgeProtocol.METHOD_DESCRIBE, BridgeProtocol.describeRequest(name, args, language)) ?: return@withContext null
    val title = (result["title"] as? kotlinx.serialization.json.JsonPrimitive)?.content
    val detail = (result["detail"] as? kotlinx.serialization.json.JsonPrimitive)?.content
    if (title == null) null else title to detail
  }

  override suspend fun run(authority: String, name: String, args: JsonObject, confirmed: Boolean, requestId: String, language: String): RemoteResult = withContext(Dispatchers.IO) {
    when (val raw = callRaw(authority, BridgeProtocol.METHOD_RUN, BridgeProtocol.runRequest(name, args, confirmed, requestId, language))) {
      is CallResult.Ok -> BridgeProtocol.resultOf(BridgeProtocol.parse(raw.bundle.getString(BridgeProtocol.KEY_JSON))) ?: RemoteResult.Error("risposta illeggibile dall'app")
      is CallResult.Denied -> RemoteResult.Error("l'app rifiuta la chiamata (firma diversa: e' una build di sviluppo?)")
      is CallResult.Missing -> RemoteResult.Error("l'app non espone piu' i tool (disinstallata o da aggiornare)")
      is CallResult.Failed -> RemoteResult.Error("l'app non risponde: aprila e riprova (${raw.message})")
    }
  }

  override suspend fun status(authority: String, jobId: String): RemoteResult = withContext(Dispatchers.IO) {
    when (val raw = callRaw(authority, BridgeProtocol.METHOD_STATUS, BridgeProtocol.statusRequest(jobId))) {
      is CallResult.Ok -> BridgeProtocol.resultOf(BridgeProtocol.parse(raw.bundle.getString(BridgeProtocol.KEY_JSON))) ?: RemoteResult.Error("stato illeggibile")
      is CallResult.Denied -> RemoteResult.Error("permesso negato")
      is CallResult.Missing -> RemoteResult.Error("l'app non risponde piu'")
      is CallResult.Failed -> RemoteResult.Error("l'app non risponde: ${raw.message}")
    }
  }

  override suspend fun cancel(authority: String, jobId: String) {
    withContext(Dispatchers.IO) { callRaw(authority, BridgeProtocol.METHOD_CANCEL, BridgeProtocol.statusRequest(jobId)) }
  }

  /** Legge una parte consegnata dall'ospite (URI con grant temporaneo). */
  override fun readPart(part: RemotePart): ByteArray? = runCatching {
    context.contentResolver.openInputStream(Uri.parse(part.uri))?.use { it.readBytes() }
  }.getOrNull()

  private fun callJson(authority: String, method: String, request: JsonObject): JsonObject? =
    (callRaw(authority, method, request) as? CallResult.Ok)?.let { BridgeProtocol.parse(it.bundle.getString(BridgeProtocol.KEY_JSON)) }

  private sealed interface CallResult {
    class Ok(val bundle: Bundle) : CallResult
    data object Denied : CallResult
    data object Missing : CallResult
    class Failed(val message: String) : CallResult
  }

  private fun callRaw(authority: String, method: String, request: JsonObject?): CallResult {
    val client = runCatching { context.contentResolver.acquireUnstableContentProviderClient(authority) }
      .getOrElse { return if (it is SecurityException) CallResult.Denied else CallResult.Failed(it.message ?: "errore") }
      ?: return CallResult.Missing
    return try {
      val extras = Bundle().apply { request?.let { putString(BridgeProtocol.KEY_JSON, it.toString()) } }
      val bundle = client.call(method, null, extras) ?: return CallResult.Failed("nessuna risposta")
      CallResult.Ok(bundle)
    } catch (e: SecurityException) {
      CallResult.Denied
    } catch (e: DeadObjectException) {
      CallResult.Failed("processo terminato")
    } catch (e: Throwable) {
      CallResult.Failed(e.message ?: e::class.simpleName ?: "errore")
    } finally {
      runCatching { client.close() }
    }
  }
}

/** Un'app ospite trovata sul telefono. */
data class RemoteHost(val packageName: String, val authority: String, val label: String)
