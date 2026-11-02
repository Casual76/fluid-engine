package dev.antigravity.fluidengine.ai.orchestrator

import dev.antigravity.fluidengine.ai.keys.AiSettings
import dev.antigravity.fluidengine.ai.net.RateLimitInfo
import dev.antigravity.fluidengine.ai.provider.ChatDelta
import dev.antigravity.fluidengine.ai.provider.ChatProvider
import dev.antigravity.fluidengine.ai.provider.ChatRequest
import dev.antigravity.fluidengine.ai.provider.ChatTurn
import dev.antigravity.fluidengine.ai.provider.FinishReason
import dev.antigravity.fluidengine.ai.provider.Message
import dev.antigravity.fluidengine.ai.provider.ModelCatalogue
import dev.antigravity.fluidengine.ai.provider.ProviderId
import dev.antigravity.fluidengine.ai.provider.ReadyProvider
import dev.antigravity.fluidengine.ai.provider.TranscribeOptions
import dev.antigravity.fluidengine.ai.provider.Transcript
import dev.antigravity.fluidengine.ai.tools.AiTool
import dev.antigravity.fluidengine.ai.tools.AiToolGroup
import dev.antigravity.fluidengine.ai.tools.Schema
import dev.antigravity.fluidengine.ai.tools.ToolOutput
import dev.antigravity.fluidengine.ai.tools.ToolRegistry
import java.io.File
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.JsonObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * I gruppi dell'app finta. Il tempo di lavoro e' in millisecondi veri e piccoli: i test girano in
 * tempo reale, con margini larghi (un tool che deve passare ha almeno il doppio di cio' che serve).
 */
private enum class TtGroup(
  override val id: String,
  override val statusKey: String,
  override val hint: String,
  override val toolTimeoutMillis: Long?,
  override val awaitsConfirmation: Boolean = false,
) : AiToolGroup {
  /** Legge: un tetto suo, corto. */
  READ("lettura", "read", "legge", 150L),

  /** Nessun tetto suo: vale quello della configurazione. */
  OPEN("aperto", "open", "senza tetto proprio", null),

  /** Scrive e aspetta l'utente: il tempo di lavoro e' corto, la conferma lo allunga. */
  WRITE("scrittura", "write", "scrive", 150L, awaitsConfirmation = true),

  /** Come WRITE, con i venti secondi di lavoro di FluidWeather. */
  WRITE20("scrittura20", "write", "scrive con venti secondi", 20_000L, awaitsConfirmation = true),
}

private class TtCtx

private sealed interface TtTurn {
  data class Calls(val names: List<String>) : TtTurn
  data class Text(val text: String) : TtTurn
}

private class TtProvider(turns: List<TtTurn>) : ChatProvider {
  override val id = ProviderId.GROQ
  private val queue = ArrayDeque(turns)
  val streamed = mutableListOf<ChatRequest>()

  override suspend fun complete(request: ChatRequest): ChatTurn =
    ChatTurn(Message.Assistant("""{"gruppi":["lettura"],"profondo":false}"""), FinishReason.STOP, null, RateLimitInfo.EMPTY)

  override fun stream(request: ChatRequest): Flow<ChatDelta> = flow {
    streamed += request.copy(messages = request.messages.toList())
    when (val turn = queue.removeFirstOrNull() ?: TtTurn.Text("(fine copione)")) {
      is TtTurn.Text -> {
        emit(ChatDelta.Text(turn.text))
        emit(ChatDelta.Finish(FinishReason.STOP, null, RateLimitInfo.EMPTY))
      }
      is TtTurn.Calls -> {
        turn.names.forEachIndexed { index, name -> emit(ChatDelta.ToolCallPart(index, "call_$index", name, "{}")) }
        emit(ChatDelta.Finish(FinishReason.TOOL_CALLS, null, RateLimitInfo.EMPTY))
      }
    }
  }

  override suspend fun listModels(): ModelCatalogue = ModelCatalogue(emptyList(), emptyList())
  override suspend fun transcribe(audio: File, mime: String, options: TranscribeOptions): Transcript = Transcript("", null)
}

/** Un tool che lavora per [workMillis] e basta. */
private class TtWorker(
  override val name: String,
  override val group: AiToolGroup,
  private val workMillis: Long,
  override val needsConfirmation: Boolean = false,
  override val timeoutMillis: Long? = group.toolTimeoutMillis,
) : AiTool<TtCtx> {
  override val description = "lavora"
  override val parameters: JsonObject = Schema.obj(emptyMap())
  override suspend fun run(args: JsonObject, ctx: TtCtx): ToolOutput {
    delay(workMillis)
    return ToolOutput("$name finito")
  }
}

/** Un tool che scrive: chiede il si' al cancello, e se arriva lavora per [workMillis]. */
private class TtWriter(
  override val name: String,
  override val group: AiToolGroup,
  private val gate: AiConfirmationGate,
  private val workMillis: Long = 0L,
) : AiTool<TtCtx> {
  override val description = "scrive"
  override val parameters: JsonObject = Schema.obj(emptyMap())
  override suspend fun run(args: JsonObject, ctx: TtCtx): ToolOutput = when (gate.ask("Confermi $name?", null)) {
    ConfirmationOutcome.CONFIRMED -> {
      delay(workMillis)
      ToolOutput("$name fatto")
    }
    ConfirmationOutcome.REJECTED -> ToolOutput("$name rifiutato")
    ConfirmationOutcome.TIMEOUT -> ToolOutput("$name senza risposta")
  }
}

/** Chi tocca Conferma: risponde si' a ogni richiesta, [afterMillis] dopo che compare. */
private suspend fun confirmEach(gate: AiConfirmationGate, afterMillis: Long) {
  while (true) {
    val pending = gate.current.filterNotNull().first()
    delay(afterMillis)
    gate.resolve(pending.id, true)
    gate.current.first { it == null || it.id != pending.id }
  }
}

private const val TIMED_OUT = "errore: lo strumento non ha risposto in tempo"

class AiToolTimeoutTest {

  private val router = AiRouter(TtGroup.entries, null, "un test", defaultGroups = listOf(TtGroup.READ))

  private fun registry(vararg tools: AiTool<TtCtx>) = ToolRegistry(tools.toList(), TtGroup.entries)

  private fun orchestrator(registry: ToolRegistry<TtCtx>, config: AiOrchestratorConfig = AiOrchestratorConfig(), gate: AiConfirmationGate? = null) =
    AiOrchestrator(registry, router, AiDiagnosticsLog(), config = config, gate = gate)

  /** Una domanda con un giro di tool call e una risposta: cosa ha risposto ogni tool, per nome. */
  private fun ask(
    registry: ToolRegistry<TtCtx>,
    calls: List<String>,
    config: AiOrchestratorConfig = AiOrchestratorConfig(),
    gate: AiConfirmationGate? = null,
    beside: suspend CoroutineScope.() -> Unit = {},
  ): Map<String, String> = runBlocking {
    val provider = TtProvider(listOf(TtTurn.Calls(calls), TtTurn.Text("fatto")))
    val input = AskInput(
      question = "fai",
      mode = AskMode.TEXT,
      language = "it",
      settings = AiSettings(),
      providers = listOf(ReadyProvider(provider, "modello", "stt", "piccolo")),
      toolContext = TtCtx(),
      systemPrompt = "sei un assistente",
      conversation = Conversation(1L, 0L),
      actionsEnabled = true,
      preselectedGroups = TtGroup.entries.toSet(),
    )
    val helper = launch(Dispatchers.Default) { beside() }
    val result = orchestrator(registry, config, gate).ask(input, MutableStateFlow(AssistantState.Idle))
    helper.cancel()
    assertEquals("fatto", result.answer)
    provider.streamed[1].messages.filterIsInstance<Message.ToolResult>().associate { it.name to it.content }
  }

  @Test
  fun `un gruppo con il suo tetto taglia il tool lento, e uno senza tetto proprio usa quello della configurazione`() {
    val results = ask(
      registry(TtWorker("lento", TtGroup.READ, workMillis = 700), TtWorker("calmo", TtGroup.OPEN, workMillis = 300)),
      calls = listOf("lento", "calmo"),
    )
    // Il gruppo READ dice 150 ms: il tool da 700 non arriva. OPEN non dice niente: valgono i 30 s della configurazione.
    assertEquals(TIMED_OUT, results.getValue("lento"))
    assertEquals("calmo finito", results.getValue("calmo"))
  }

  @Test
  fun `il tetto della configurazione e' quello di chi non ne dichiara uno, e basta a tagliare`() {
    val results = ask(
      registry(TtWorker("calmo", TtGroup.OPEN, workMillis = 700)),
      calls = listOf("calmo"),
      config = AiOrchestratorConfig(toolTimeoutMillis = 150L),
    )
    assertEquals(TIMED_OUT, results.getValue("calmo"))
  }

  @Test
  fun `un tool che aspetta una conferma ha il tempo di lavoro piu' la finestra del cancello`() {
    val gate = AiConfirmationGate(timeoutMillis = 800)
    val results = ask(
      registry(TtWriter("scrivi", TtGroup.WRITE, gate, workMillis = 60)),
      calls = listOf("scrivi"),
      gate = gate,
      // L'utente tocca Conferma dopo 400 ms, molto oltre i 150 ms di lavoro del gruppo: la scheda
      // c'e' ancora e il tool, che prima veniva tagliato, finisce.
      beside = { confirmEach(gate, afterMillis = 400) },
    )
    assertEquals("scrivi fatto", results.getValue("scrivi"))
  }

  @Test
  fun `lo stesso tool in un gruppo che non aspetta conferme viene tagliato mentre la scheda e' a schermo, e la scheda sparisce`() {
    val gate = AiConfirmationGate(timeoutMillis = 800)
    val results = ask(
      registry(TtWriter("scrivi", TtGroup.READ, gate, workMillis = 60)),
      calls = listOf("scrivi"),
      gate = gate,
      beside = { confirmEach(gate, afterMillis = 400) },
    )
    assertEquals(TIMED_OUT, results.getValue("scrivi"))
    assertNull("il tool cancellato toglie la sua richiesta dal cancello", gate.current.value)
  }

  @Test
  fun `needsConfirmation basta a far aspettare, l'attesa non dipende dal gruppo`() {
    val gate = AiConfirmationGate(timeoutMillis = 800)
    val flagged = object : AiTool<TtCtx> {
      private val inner = TtWriter("scrivi", TtGroup.READ, gate, workMillis = 60)
      override val name = inner.name
      override val group = inner.group
      override val description = inner.description
      override val parameters = inner.parameters
      override val needsConfirmation = true
      override suspend fun run(args: JsonObject, ctx: TtCtx) = inner.run(args, ctx)
    }
    val results = ask(registry(flagged), calls = listOf("scrivi"), gate = gate, beside = { confirmEach(gate, afterMillis = 400) })
    assertEquals("scrivi fatto", results.getValue("scrivi"))
  }

  // ----------------------------------------------------------------- il conto, senza aspettare

  @Test
  fun `il tetto di un tool che non aspetta conferme e' il suo tempo, mai oltre il budget meno cinque secondi, mai sotto i tre`() {
    val reg = registry(TtWorker("lettura", TtGroup.READ, 0), TtWorker("aperto", TtGroup.OPEN, 0))
    val o = orchestrator(reg)
    val read = reg.find("lettura")!!
    val open = reg.find("aperto")!!
    assertEquals(150L, o.toolTimeoutMillis(read, budgetRemainingMillis = 90_000))
    assertEquals("la configurazione ha 30 s", 30_000L, o.toolTimeoutMillis(open, budgetRemainingMillis = 200_000))
    assertEquals("meno cinque secondi di budget", 15_000L, o.toolTimeoutMillis(open, budgetRemainingMillis = 20_000))
    assertEquals("mai sotto i tre secondi", 3_000L, o.toolTimeoutMillis(open, budgetRemainingMillis = 6_000))
    assertEquals(150L, o.toolTimeoutMillis(read, budgetRemainingMillis = 1_000))
  }

  @Test
  fun `venti secondi di lavoro e una conferma da sessanta, la somma la fa l'orchestratore e non si puo' sbagliare`() {
    // La combinazione di FluidWeather: venti secondi per tool, sessanta per toccare Conferma.
    val config = AiOrchestratorConfig(toolTimeoutMillis = 20_000L, confirmationTimeoutMillis = 60_000L, finalReserveMillis = 15_000L)
    val gate = AiConfirmationGate(config)
    val reg = registry(TtWriter("scrivi", TtGroup.WRITE20, gate), TtWorker("leggi", TtGroup.OPEN, 0))
    val o = orchestrator(reg, config)
    assertEquals(60_000L, gate.timeoutMillis)
    assertEquals(60_000L, o.confirmationWindowMillis)
    val write = reg.find("scrivi")!!
    val read = reg.find("leggi")!!
    assertEquals("chi legge: venti secondi", 20_000L, o.toolTimeoutMillis(read, budgetRemainingMillis = 90_000))
    assertEquals("chi scrive: venti di lavoro e sessanta di conferma", 80_000L, o.toolTimeoutMillis(write, budgetRemainingMillis = 200_000))
    assertEquals("col budget di una card, la riserva per rispondere resta", 75_000L, o.toolTimeoutMillis(write, budgetRemainingMillis = 90_000))
  }

  @Test
  fun `due conferme nello stesso giro valgono due finestre, e mai meno di una`() {
    val gate = AiConfirmationGate(timeoutMillis = 30_000)
    val reg = registry(TtWriter("scrivi", TtGroup.WRITE, gate))
    val o = orchestrator(reg, gate = gate)
    val write = reg.find("scrivi")!!
    assertEquals(150L + 30_000, o.toolTimeoutMillis(write, 500_000))
    assertEquals(150L + 60_000, o.toolTimeoutMillis(write, 500_000, confirmationsInRound = 2))
    assertEquals(150L + 90_000, o.toolTimeoutMillis(write, 500_000, confirmationsInRound = 3))
    assertEquals("zero conferme in giro non azzera la finestra", 150L + 30_000, o.toolTimeoutMillis(write, 500_000, confirmationsInRound = 0))
  }

  @Test
  fun `il cancello dato all'orchestratore vale piu' del numero della configurazione`() {
    val config = AiOrchestratorConfig(confirmationTimeoutMillis = 10_000L)
    assertEquals(10_000L, orchestrator(registry(), config).confirmationWindowMillis)
    assertEquals(45_000L, orchestrator(registry(), config, gate = AiConfirmationGate(timeoutMillis = 45_000)).confirmationWindowMillis)
    assertEquals("senza niente, la finestra di serie del cancello", AiConfirmationGate.DEFAULT_TIMEOUT_MILLIS, orchestrator(registry()).confirmationWindowMillis)
  }

  @Test
  fun `il tempo dichiarato dal tool batte quello del gruppo`() {
    val reg = registry(TtWorker("preciso", TtGroup.READ, 0, timeoutMillis = 5_000L), TtWorker("generico", TtGroup.READ, 0))
    val o = orchestrator(reg)
    assertEquals(5_000L, o.toolTimeoutMillis(reg.find("preciso")!!, 100_000))
    assertEquals(150L, o.toolTimeoutMillis(reg.find("generico")!!, 100_000))
  }

  @Test
  fun `i flag nuovi hanno un default che non cambia niente, nessun tetto proprio e nessuna attesa`() {
    val plain = object : AiToolGroup {
      override val id = "x"
      override val statusKey = "x"
      override val hint = "x"
    }
    assertNull(plain.toolTimeoutMillis)
    assertTrue(!plain.awaitsConfirmation)
    val tool = object : AiTool<TtCtx> {
      override val name = "t"
      override val group: AiToolGroup = plain
      override val description = ""
      override val parameters: JsonObject = Schema.obj(emptyMap())
      override suspend fun run(args: JsonObject, ctx: TtCtx) = ToolOutput("")
    }
    assertNull(tool.timeoutMillis)
    assertTrue(!tool.awaitsConfirmation)
    val orchestrator = AiOrchestrator(ToolRegistry(listOf(tool), listOf(plain)), router, AiDiagnosticsLog())
    // Il conto di prima: il tetto della configurazione, meno cinque secondi di budget.
    assertEquals(30_000L, orchestrator.toolTimeoutMillis(tool, 240_000))
    assertEquals(15_000L, orchestrator.toolTimeoutMillis(tool, 20_000))
  }
}
