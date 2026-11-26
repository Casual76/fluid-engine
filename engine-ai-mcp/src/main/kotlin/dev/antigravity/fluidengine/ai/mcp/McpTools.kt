package dev.antigravity.fluidengine.ai.mcp

import dev.antigravity.fluidengine.ai.orchestrator.AiConfirmationGate
import dev.antigravity.fluidengine.ai.orchestrator.ConfirmationOutcome
import dev.antigravity.fluidengine.ai.tools.AiTool
import dev.antigravity.fluidengine.ai.tools.AiToolCategory
import dev.antigravity.fluidengine.ai.tools.AiToolGroup
import dev.antigravity.fluidengine.ai.tools.ConfirmationText
import dev.antigravity.fluidengine.ai.tools.ToolError
import dev.antigravity.fluidengine.ai.tools.ToolOutcome
import dev.antigravity.fluidengine.ai.tools.ToolOutput
import dev.antigravity.fluidengine.ai.tools.ToolRegistry
import dev.antigravity.fluidengine.ai.tools.ToolResultDetails
import kotlinx.coroutines.CancellationException
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

/** La categoria di un connettore nel catalogo: una porta, come un'app collegata (4.0, F9.2). */
data class McpCategory(override val id: String, override val label: String, override val hint: String) : AiToolCategory

/**
 * Il gruppo di un connettore. Sta fra i gruppi **differiti** del catalogo ([withConnectors]): non entra mai
 * da solo nel giro, si trova con `cerca_strumenti`. Aspetta conferme ([awaitsConfirmation]), quindi
 * l'orchestratore gli lascia la finestra del cancello oltre ai suoi trenta secondi di lavoro.
 */
class McpGroup(
  override val id: String,
  override val hint: String,
  override val category: AiToolCategory,
  val connectorId: String,
) : AiToolGroup {
  override val statusKey: String = "connector"
  override val toolTimeoutMillis: Long = 30_000L
  override val awaitsConfirmation: Boolean = true
  override fun toString(): String = id
}

/**
 * Cosa l'app deve saper fare per gli strumenti dei connettori: chiedere la conferma col suo cancello e
 * dire se le azioni sono accese. E' lo stesso contratto di `RemoteToolHost` del ponte fra app.
 */
interface McpToolHost<C> {
  /** Null se si puo' procedere, o il testo per il modello (annullato, scaduto). */
  suspend fun confirm(ctx: C, text: ConfirmationText): ToolOutput?

  /** Falso se l'utente ha spento le azioni: allora gli strumenti che non leggono e basta non partono. */
  fun actionsEnabled(ctx: C): Boolean = true

  companion object {
    /** L'host di serie: il cancello delle conferme dell'app, con le parole di sempre per il modello. */
    fun <C> gate(gate: AiConfirmationGate, actions: (C) -> Boolean = { true }): McpToolHost<C> = object : McpToolHost<C> {
      override suspend fun confirm(ctx: C, text: ConfirmationText): ToolOutput? = when (gate.ask(text.title, text.detail)) {
        ConfirmationOutcome.CONFIRMED -> null
        ConfirmationOutcome.REJECTED -> ToolOutput("l'utente ha annullato: non riprovare", result = ToolResultDetails(ToolOutcome.ERROR, error = ToolError("rejected", "l'utente ha annullato")))
        ConfirmationOutcome.TIMEOUT -> ToolOutput("nessuna conferma dall'utente: l'azione non e' partita", result = ToolResultDetails(ToolOutcome.ERROR, error = ToolError("timeout", "nessuna conferma")))
      }

      override fun actionsEnabled(ctx: C): Boolean = actions(ctx)
    }
  }
}

/**
 * Uno strumento di un connettore visto dall'assistente (4.0, F9.2): lo stesso `AiTool` di sempre, con il
 * nome prefissato (`mcp_<connettore>_<tool>`), la descrizione passata dalle difese ([McpGuard.review]) e
 * lo schema ripulito ([McpGuard.schema]). [run] chiede prima la conferma (se la regola dell'utente e'
 * [McpToolPolicy.ASK]), poi chiama il server, e il risultato torna col suo tetto ([McpGuard.output]).
 *
 * Un collegamento interrotto a meta' e' **incerto**: il modello lo sa ("non so se e' stato fatto"), e la
 * chiamata non si ripete da sola.
 */
class McpTool<C>(
  val connector: McpConnector,
  val info: McpToolInfo,
  val review: McpToolReview,
  override val name: String,
  override val group: AiToolGroup,
  private val client: McpClient,
  private val host: McpToolHost<C>,
  val policy: McpToolPolicy,
  override val parameters: JsonObject,
  private val resultChars: Int = McpGuard.RESULT_CHARS,
) : AiTool<C> {
  override val description: String get() = review.modelDescription

  /** Legge e basta, secondo il server: un indizio, per le azioni spente, le parole della conferma e la regola «Sempre». */
  private val readOnly: Boolean get() = info.annotations.readOnlyHint()

  /**
   * Chiede conferma: sempre, tranne uno strumento che il server dice di sola lettura e a cui l'utente ha dato «Sempre».
   * Una scrittura chiede comunque, anche con «Sempre» salvato (la regola non si applica: [McpConnectors.setPolicy]).
   */
  private val confirms: Boolean get() = policy != McpToolPolicy.ALWAYS || !readOnly

  override val isAction: Boolean get() = !readOnly
  override val needsConfirmation: Boolean get() = confirms
  override val awaitsConfirmation: Boolean get() = confirms

  override suspend fun describe(args: JsonObject, ctx: C): ConfirmationText = confirmation(args)

  /** Le parole della conferma: cosa, di quale connettore, con quali argomenti (accorciati). */
  fun confirmation(args: JsonObject): ConfirmationText {
    val title = McpGuard.clean(info.title ?: info.annotations.title ?: info.name.replace('_', ' ')).take(60)
    val verb = if (readOnly) "Leggere" else "Usare"
    val detail = args.entries.take(6).joinToString("\n") { (key, value) ->
      val shown = (value as? JsonPrimitive)?.content ?: value.toString()
      "$key: ${McpGuard.clean(shown).take(80)}"
    }.ifBlank { null }
    return ConfirmationText("$verb «$title» di ${connector.name}?", detail)
  }

  override suspend fun run(args: JsonObject, ctx: C): ToolOutput {
    if (policy == McpToolPolicy.OFF) return ToolOutput.error("strumento spento dall'utente nella pagina dei connettori")
    if (!readOnly && !host.actionsEnabled(ctx)) return ToolOutput.error("le azioni sono disattivate nelle impostazioni: dillo all'utente")
    if (confirms) host.confirm(ctx, confirmation(args))?.let { return it }
    return try {
      McpGuard.output(client.callTool(info.name, args), connector.name, resultChars)
    } catch (e: CancellationException) {
      throw e
    } catch (e: McpError.AuthRequired) {
      ToolOutput.error("il connettore ${connector.name} chiede di entrare di nuovo: dillo all'utente (pagina Connettori)")
    } catch (e: McpError) {
      if (e.uncertain) {
        ToolOutput(
          "errore: ${e.message}; non so se l'azione e' stata fatta: non riprovare da solo, dillo all'utente",
          result = ToolResultDetails(ToolOutcome.UNCERTAIN, error = ToolError("connector_uncertain", e.message.orEmpty())),
        )
      } else {
        ToolOutput.error(e.message ?: "errore del connettore")
      }
    }
  }
}

/**
 * Vero se il server dice che lo strumento legge e basta (e non che distrugge). E' un indizio: serve solo a offrire
 * «Sempre» nella pagina, e una scrittura dichiarata cosi' per sbaglio (o per inganno) resta sotto la regola dell'utente.
 */
fun McpToolAnnotations.readOnlyHint(): Boolean = readOnlyHint == true && destructiveHint != true

/** Gli strumenti di un connettore pronti per il catalogo: la categoria, il gruppo, i tool. */
class McpToolSet<C>(
  val connector: McpConnector,
  val category: McpCategory,
  val group: McpGroup,
  val tools: List<McpTool<C>>,
  /** Gli strumenti fuori dal catalogo e perche': nuovi da approvare, cambiati dal server, spenti. */
  val held: Map<String, McpHeld>,
) {
  /** Le parole per la ricerca: il nome del connettore e i titoli degli strumenti. */
  val searchTerms: List<String> get() = listOf(connector.name) + tools.mapNotNull { it.info.title }

  companion object {
    /**
     * Costruisce gli strumenti di [connector] dall'ultimo elenco salvato. Entrano solo quelli approvati e
     * uguali a come l'utente li ha visti; [taken] sono i nomi gia' usati nel catalogo (un doppione prende
     * un numero in coda).
     */
    fun <C> of(connector: McpConnector, client: McpClient, host: McpToolHost<C>, taken: Set<String> = emptySet()): McpToolSet<C> {
      val slug = McpNames.slug(connector.name, connector.id)
      val category = McpCategory("mcp_$slug", connector.name, "il connettore ${connector.name}" + (connector.server?.title?.let { " ($it)" } ?: ""))
      val group = McpGroup("mcp_$slug", "gli strumenti del connettore ${connector.name}", category, connector.id)
      val names = taken.toMutableSet()
      val held = linkedMapOf<String, McpHeld>()
      val others = taken + connector.tools.map { it.name }
      val tools = connector.tools.mapNotNull { info ->
        val approval = connector.approvals[info.name]
        val state = when {
          approval == null -> McpHeld.NEW
          approval.hash != McpGuard.hash(info) -> McpHeld.CHANGED
          approval.policy == McpToolPolicy.OFF -> McpHeld.OFF
          else -> null
        }
        if (state != null) {
          held[info.name] = state
          return@mapNotNull null
        }
        val name = McpNames.unique("mcp_${slug}_${McpNames.clean(info.name)}", names)
        names += name
        McpTool(
          connector = connector,
          info = info,
          review = McpGuard.review(info, connector.name, others),
          name = name,
          group = group,
          client = client,
          host = host,
          policy = approval!!.policy,
          parameters = McpGuard.schema(info.inputSchema, others),
        )
      }
      return McpToolSet(connector, category, group, tools, held)
    }
  }
}

/** Perche' uno strumento di un connettore non e' nel catalogo. */
enum class McpHeld {
  /** Comparso dopo l'aggiunta del connettore: l'utente non l'ha ancora visto. */
  NEW,

  /** Il server l'ha cambiato (descrizione, schema, indicazioni) dopo che l'utente l'aveva approvato. */
  CHANGED,

  /** Spento dall'utente. */
  OFF,
}

/**
 * Il catalogo dell'app con i connettori in piu' (4.0, F9.2): i loro gruppi entrano come **differiti**, con
 * le loro parole per la ricerca. Gli strumenti dell'app restano quelli di prima, al loro posto.
 */
fun <C> ToolRegistry<C>.withConnectors(sets: List<McpToolSet<C>>): ToolRegistry<C> {
  val live = sets.filter { it.tools.isNotEmpty() }
  if (live.isEmpty()) return this
  return ToolRegistry(
    tools = tools + live.flatMap { it.tools },
    groups = groups + live.map { it.group },
    actionGroup = actionGroup,
    deferred = deferred + live.map { it.group },
    searchTerms = searchTerms + live.associate { it.group to it.searchTerms },
  )
}

/** I nomi degli strumenti dei connettori: lettere, cifre e trattini bassi, 64 caratteri al massimo (il limite dei provider). */
object McpNames {
  const val MAX = 64

  fun clean(text: String): String {
    val plain = java.text.Normalizer.normalize(text, java.text.Normalizer.Form.NFD).replace(Regex("\\p{M}+"), "")
    return plain.lowercase().replace(Regex("[^a-z0-9_]+"), "_").trim('_').ifEmpty { "tool" }
  }

  /** La parte del connettore nel nome: corta, dal nome, o dall'id se il nome non ha lettere. */
  fun slug(name: String, id: String): String = clean(name).take(12).trim('_').ifEmpty { clean(id).take(8) }

  fun unique(base: String, taken: Set<String>): String {
    val trimmed = base.take(MAX)
    if (trimmed !in taken) return trimmed
    var n = 2
    while (true) {
      val suffix = "_$n"
      val candidate = trimmed.take(MAX - suffix.length) + suffix
      if (candidate !in taken) return candidate
      n++
    }
  }
}
