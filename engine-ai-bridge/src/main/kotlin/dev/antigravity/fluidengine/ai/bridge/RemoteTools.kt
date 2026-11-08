package dev.antigravity.fluidengine.ai.bridge

import dev.antigravity.fluidengine.ai.provider.ContentPart
import dev.antigravity.fluidengine.ai.tools.AiTool
import dev.antigravity.fluidengine.ai.tools.AiToolCategory
import dev.antigravity.fluidengine.ai.tools.AiToolGroup
import dev.antigravity.fluidengine.ai.tools.ConfirmationText
import dev.antigravity.fluidengine.ai.tools.*
import java.util.UUID
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.coroutines.delay
import kotlinx.serialization.json.JsonObject

/** Una categoria di un'app ospite, nel registry del cliente. */
data class RemoteCategory(override val id: String, override val label: String, override val hint: String) : AiToolCategory

/** Un gruppo di un'app ospite, con l'id prefissato dal dominio (`cv_voti`). */
class RemoteGroup(
  override val id: String,
  override val statusKey: String,
  override val hint: String,
  override val category: AiToolCategory?,
  override val loadsWithCategory: Boolean,
  /** Il gruppo padre (terzo livello), risolto dopo aver creato tutti i gruppi. */
  var parentGroup: RemoteGroup? = null,
  /** Il nome originale nell'ospite, per rifare il percorso all'indietro. */
  val remoteId: String,
) : AiToolGroup {
  override val parent: AiToolGroup? get() = parentGroup
  override fun toString(): String = id
}

/**
 * Cosa il cliente deve saper fare per un tool remoto: chiedere la conferma con il proprio cancello
 * (e le proprie azioni fidate), dire in che lingua, e — per i lavori lunghi — quanto aspettare.
 */
interface RemoteToolHost<C> {
  /** Torna null se si puo' procedere, o il testo da restituire al modello (rifiutato, scaduto, azioni spente). */
  suspend fun confirm(ctx: C, toolName: String, text: ConfirmationText): ToolOutput?

  fun language(ctx: C): String = "it"

  /** Vero se l'utente ha spento le azioni: i tool azione rispondono senza chiamare l'ospite. */
  fun actionsEnabled(ctx: C): Boolean = true

  val actionsOffText: String get() = "le azioni sono disattivate nelle impostazioni: dillo all'utente"

  val longRunningTimeoutMillis: Long get() = 180_000L
}

/** Le chiamate che un tool remoto fa attraverso il confine: [AiToolClient] le implementa, i test le fingono. */
interface BridgeCalls {
  suspend fun describe(authority: String, name: String, args: JsonObject, language: String): Pair<String?, String?>?
  suspend fun run(authority: String, name: String, args: JsonObject, confirmed: Boolean, requestId: String, language: String): RemoteResult
  suspend fun status(authority: String, jobId: String): RemoteResult
  suspend fun cancel(authority: String, jobId: String)
  fun readPart(part: RemotePart): ByteArray?
}

/**
 * Un tool di un'app ospite visto dal cliente: stesso nome prefissato dal dominio, stessa
 * descrizione con l'app fra parentesi, e il `run` che attraversa il confine. La conferma la chiede
 * il cliente **prima** (con `describe` dell'ospite, o il titolo generico), poi chiama `run` con
 * `confirmed = true`; un lavoro lungo si segue con `status` ogni secondo.
 */
class RemoteTool<C>(
  private val client: BridgeCalls,
  val catalog: RemoteCatalog,
  val info: RemoteToolInfo,
  override val group: AiToolGroup,
  private val host: RemoteToolHost<C>,
) : AiTool<C> {
  override val name: String = "${catalog.domain}_${info.name}"
  override val description: String = "${info.description} (${catalog.appLabel})"
  override val parameters: JsonObject = info.parameters
  override val needsConfirmation: Boolean = info.needsConfirmation
  override val longRunning: Boolean = info.longRunning
  override val isAction: Boolean = info.action
  override val timeoutMillis: Long? get() = if (longRunning) host.longRunningTimeoutMillis + 2_000L else info.timeoutMillis


  override suspend fun describe(args: JsonObject, ctx: C): ConfirmationText? {
    if (!info.needsConfirmation) return null
    val remote = client.describe(catalog.authority, info.name, args, host.language(ctx))
    return ConfirmationText(remote?.first ?: "${catalog.appLabel}: ${info.name.replace('_', ' ')}?", remote?.second)
  }

  override suspend fun run(args: JsonObject, ctx: C): ToolOutput =
    if (info.action || info.needsConfirmation) ToolOutput.error("operation identity required for writes")
    else run(args, ctx, ToolExecution(UUID.randomUUID().toString()))

  override suspend fun run(args: JsonObject, ctx: C, execution: ToolExecution): ToolOutput {
    ToolArgumentValidator.error(args, parameters)?.let { return ToolOutput.error(it) }
    if (info.action && !host.actionsEnabled(ctx)) return ToolOutput(host.actionsOffText)
    if (info.needsConfirmation) {
      val text = describe(args, ctx) ?: ConfirmationText("${catalog.appLabel}: ${info.name.replace('_', ' ')}?", null)
      host.confirm(ctx, name, text)?.let { return it }
    }
    val requestId = execution.operationId
    val language = host.language(ctx)
    val output = when (val result = client.run(catalog.authority, info.name, args, confirmed = info.needsConfirmation, requestId = requestId, language = language)) {
      is RemoteResult.Done -> output(result.text, result.escalate, result.parts, result.result)
      is RemoteResult.Error -> uncertain(result.message)
      is RemoteResult.Status -> output(result.text ?: result.detail ?: "in corso", result.escalate, result.parts, result.result)
      is RemoteResult.Job -> follow(result.jobId)
    }
    return output.copy(result = output.result?.let { it.copy(operation = it.operation ?: ToolOperationRef(requestId, catalog.appId)) })
  }

  private suspend fun follow(jobId: String): ToolOutput {
    val started = System.currentTimeMillis()
    try {
      while (true) {
        delay(1_000L)
        when (val status = client.status(catalog.authority, jobId)) {
          is RemoteResult.Status -> when (status.state) {
            BridgeProtocol.STATE_DONE -> return output(status.text ?: "fatto", status.escalate, status.parts, status.result)
            BridgeProtocol.STATE_FAILED -> return ToolOutput.error(status.detail ?: "il lavoro e' fallito")
            BridgeProtocol.STATE_UNKNOWN -> return uncertain(status.detail ?: "lavoro perso")
          }
          is RemoteResult.Error -> return uncertain(status.message)
          else -> Unit
        }
        if (System.currentTimeMillis() - started > host.longRunningTimeoutMillis) {
          cancelJob(jobId)
          return uncertain("il lavoro in ${catalog.appLabel} non e' finito in tempo: continua nell'app")
        }
      }
    } catch (cancelled: CancellationException) {
      cancelJob(jobId)
      throw cancelled
    }
  }

  private suspend fun cancelJob(jobId: String) = withContext(NonCancellable) {
    // Binder may block despite cancellation. Bound the caller's wait, not just the IO coroutine.
    val cleanupScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    val cleanup = cleanupScope.launch { try { client.cancel(catalog.authority, jobId) } catch (_: Exception) { } }
    try { withTimeoutOrNull(2_000L) { cleanup.join() } }
    finally { cleanupScope.coroutineContext[kotlinx.coroutines.Job]?.cancel() }
    Unit
  }

  private fun uncertain(message: String): ToolOutput = ToolOutput("errore: $message", result = ToolResultDetails(
    ToolOutcome.UNCERTAIN, error = ToolError("remote_unknown_effect", message, retryable = false),
  ))

  private fun output(text: String, escalate: Boolean, parts: List<RemotePart>, result: ToolResultDetails? = null): ToolOutput {
    val loaded = parts.mapNotNull { part ->
      val bytes = client.readPart(part) ?: return@mapNotNull null
      when (part.kind) {
        RemotePart.KIND_IMAGE -> ContentPart.Image(bytes, part.mime)
        else -> ContentPart.Document(bytes, part.mime, part.name ?: "documento")
      }
    }
    return ToolOutput(text, loaded, escalate, result ?: ToolResultDetails(
      ToolOutcome.UNCERTAIN, error = ToolError("legacy_unknown_effect", "Legacy host supplied text without verified execution evidence"),
    ))
  }
}

/** Il catalogo di un ospite tradotto in gruppi e tool del registry del cliente. */
class RemoteToolSet<C>(
  val catalog: RemoteCatalog,
  val categories: List<RemoteCategory>,
  val groups: List<RemoteGroup>,
  val tools: List<RemoteTool<C>>,
) {
  companion object {
    /**
     * Costruisce gruppi e tool dal catalogo. Le categorie dell'ospite restano quelle (`classeviva`,
     * `meteo`), i gruppi e i tool prendono il prefisso del dominio; un gruppo senza categoria va
     * sotto una categoria costruita dal dominio, cosi' nel router ogni app e' una porta.
     */
    fun <C> of(client: BridgeCalls, catalog: RemoteCatalog, host: RemoteToolHost<C>): RemoteToolSet<C> {
      val fallback = RemoteCategory(catalog.domain, catalog.appLabel, catalog.hint ?: "i tool di ${catalog.appLabel}")
      val categories = catalog.categories.map { RemoteCategory(it.id, it.label, it.hint) }.ifEmpty { listOf(fallback) }
      val byId = categories.associateBy { it.id }
      val groups = catalog.groups.map { g ->
        RemoteGroup(
          id = "${catalog.domain}_${g.id}",
          statusKey = g.statusKey,
          hint = g.hint,
          category = g.category?.let { byId[it] } ?: categories.first(),
          loadsWithCategory = g.loadsWithCategory,
          remoteId = g.id,
        )
      }
      val groupsByRemote = groups.associateBy { it.remoteId }
      catalog.groups.forEach { g -> g.parent?.let { p -> groupsByRemote[g.id]?.parentGroup = groupsByRemote[p] } }
      val tools = catalog.tools.mapNotNull { t ->
        val group = groupsByRemote[t.group] ?: return@mapNotNull null
        RemoteTool(client, catalog, t, group, host)
      }
      return RemoteToolSet(catalog, categories, groups, tools)
    }
  }
}
