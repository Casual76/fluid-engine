package dev.antigravity.fluidengine.ai.orchestrator

import dev.antigravity.fluidengine.ai.keys.AiSettings
import dev.antigravity.fluidengine.ai.net.AiError
import dev.antigravity.fluidengine.ai.net.BadReason
import dev.antigravity.fluidengine.ai.net.RateLimitInfo
import dev.antigravity.fluidengine.ai.provider.ChatDelta
import dev.antigravity.fluidengine.ai.provider.ChatProvider
import dev.antigravity.fluidengine.ai.provider.ChatRequest
import dev.antigravity.fluidengine.ai.provider.ChatTurn
import dev.antigravity.fluidengine.ai.provider.FinishReason
import dev.antigravity.fluidengine.ai.provider.Message
import dev.antigravity.fluidengine.ai.provider.ModelCatalogue
import dev.antigravity.fluidengine.ai.provider.ModelInfo
import dev.antigravity.fluidengine.ai.provider.ModelKind
import dev.antigravity.fluidengine.ai.provider.ProviderId
import dev.antigravity.fluidengine.ai.provider.ReadyProvider
import dev.antigravity.fluidengine.ai.provider.ReasoningLevel
import dev.antigravity.fluidengine.ai.provider.ToolCall
import dev.antigravity.fluidengine.ai.provider.TranscribeOptions
import dev.antigravity.fluidengine.ai.provider.Transcript
import dev.antigravity.fluidengine.ai.tools.AiTool
import dev.antigravity.fluidengine.ai.tools.AiToolGroup
import dev.antigravity.fluidengine.ai.tools.Schema
import dev.antigravity.fluidengine.ai.tools.ToolOutput
import dev.antigravity.fluidengine.ai.tools.ToolRegistry
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.JsonObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** I gruppi dell'app finta di questi test. */
private enum class FoGroup(override val id: String, override val statusKey: String, override val hint: String) : AiToolGroup {
  HOURLY("orario", "hourly", "ora per ora"),
  APP("app", "app", "azioni nell'app"),
}

private class FoCtx

/** Un turno recitato: testo, un giro senza parole con la sua fine, un errore, delle tool call. */
private sealed interface FoTurn {
  data class Text(val text: String) : FoTurn
  data class Empty(val finish: FinishReason) : FoTurn
  data class Fail(val error: Throwable) : FoTurn
  data class Calls(val names: List<String>) : FoTurn
}

private class FoProvider(
  override val id: ProviderId,
  turns: List<FoTurn>,
  private val classifierError: Throwable? = null,
) : ChatProvider {
  private val queue = ArrayDeque(turns)
  val streamed = mutableListOf<ChatRequest>()
  val routed = mutableListOf<ChatRequest>()

  override suspend fun complete(request: ChatRequest): ChatTurn {
    if (request.jsonSchema == null) error("complete() non previsto: nessuno stream si spezza qui")
    routed += request
    classifierError?.let { throw it }
    return ChatTurn(Message.Assistant("""{"gruppi":["orario"],"profondo":false}"""), FinishReason.STOP, null, RateLimitInfo.EMPTY)
  }

  override fun stream(request: ChatRequest): Flow<ChatDelta> = flow {
    // Una fotografia: la lista dei messaggi del giro e' quella viva dell'orchestratore, che
    // l'accorciamento riscrive al suo posto.
    streamed += request.copy(messages = request.messages.toList())
    when (val turn = queue.removeFirstOrNull() ?: FoTurn.Text("(fine copione)")) {
      is FoTurn.Text -> {
        emit(ChatDelta.Text(turn.text))
        emit(ChatDelta.Finish(FinishReason.STOP, null, RateLimitInfo.EMPTY))
      }
      is FoTurn.Empty -> emit(ChatDelta.Finish(turn.finish, null, RateLimitInfo.EMPTY))
      is FoTurn.Fail -> throw turn.error
      is FoTurn.Calls -> {
        turn.names.forEachIndexed { index, name ->
          emit(ChatDelta.ToolCallPart(index, "call_$index", name, "{}"))
        }
        emit(ChatDelta.Finish(FinishReason.TOOL_CALLS, null, RateLimitInfo.EMPTY))
      }
    }
  }

  override suspend fun listModels(): ModelCatalogue = ModelCatalogue(emptyList(), emptyList())
  override suspend fun transcribe(audio: File, mime: String, options: TranscribeOptions): Transcript = Transcript("", null)
}

/** Un tool che porta un risultato lungo, su molte righe: la materia di una richiesta che non ci sta. */
private class FoLongTool : AiTool<FoCtx> {
  override val name = "lungo"
  override val group: AiToolGroup = FoGroup.HOURLY
  override val description = "tante righe"
  override val parameters = Schema.obj(emptyMap())
  override suspend fun run(args: JsonObject, ctx: FoCtx): ToolOutput =
    ToolOutput((1..120).joinToString("\n") { "riga $it: " + "x".repeat(20) })
}

/**
 * Il failover della 2.8.0 con provider finti: il `tool_use_failed` che si riprova e poi passa, la
 * risposta vuota che non diventa mai una risposta, la richiesta troppo lunga che si accorcia, il
 * modello sparito che si dice all'app. E il perche' di ogni cambio, nel log e negli stati.
 */
class AiFailoverOrchestratorTest {

  private val registry = ToolRegistry(listOf(FoLongTool()), FoGroup.entries, actionGroup = FoGroup.APP)
  private val router = AiRouter(FoGroup.entries, FoGroup.APP, "un test", defaultGroups = listOf(FoGroup.HOURLY))
  private val orchestrator = AiOrchestrator(registry, router, AiDiagnosticsLog())

  private fun ready(provider: FoProvider, catalogue: ModelCatalogue? = null) =
    ReadyProvider(provider, "modello-${provider.id.id}", "stt", "piccolo", deepModel = "profondo-${provider.id.id}", catalogue = catalogue)

  private fun input(
    vararg providers: ReadyProvider,
    conversation: Conversation = Conversation(1L, 0L),
    pin: Boolean = false,
    preselected: Set<AiToolGroup>? = setOf(FoGroup.HOURLY),
    onModelUnavailable: (ProviderId, String) -> Unit = { _, _ -> },
  ) = AskInput(
    question = "che tempo fa?",
    mode = AskMode.TEXT,
    language = "it",
    settings = AiSettings(),
    providers = providers.toList(),
    toolContext = FoCtx(),
    systemPrompt = "sei un assistente",
    conversation = conversation,
    actionsEnabled = false,
    preselectedGroups = preselected,
    pinProvider = pin,
    onModelUnavailable = onModelUnavailable,
  )

  private fun toolUseFailed() = AiError.BadRequest(400, "Failed to call a function.", BadReason.TOOL_USE_FAILED)
  private fun tooLong() = AiError.BadRequest(413, "Request too large", BadReason.CONTEXT_TOO_LONG)
  private fun modelGone() = AiError.BadRequest(404, "model not found", BadReason.MODEL_UNAVAILABLE)

  private suspend fun failure(block: suspend () -> Unit): AssistantFailure {
    val error = runCatching { block() }.exceptionOrNull()
    assertTrue("atteso AssistantFailure, avuto $error", error is AssistantFailure)
    return error as AssistantFailure
  }

  @Test
  fun `tool_use_failed si riprova una volta, poi si passa a Gemini e il perche' resta nel log`() = runBlocking {
    val groq = FoProvider(ProviderId.GROQ, listOf(FoTurn.Fail(toolUseFailed()), FoTurn.Fail(toolUseFailed())))
    val gemini = FoProvider(ProviderId.GEMINI, listOf(FoTurn.Text("Da Gemini: sereno.")))
    val state = MutableStateFlow<AssistantState>(AssistantState.Idle)
    val seen = mutableListOf<AssistantState>()
    val collector = launch(Dispatchers.Unconfined) { state.collect { seen += it } }
    val result = orchestrator.ask(input(ready(groq), ready(gemini)), state)
    collector.cancel()

    assertEquals("Da Gemini: sereno.", result.answer)
    assertEquals(2, groq.streamed.size)
    assertEquals(listOf(ProviderSwitch(ProviderId.GROQ, ProviderId.GEMINI, SwitchReason.TOOL_USE_FAILED)), result.log.switches)
    assertEquals(listOf(ProviderId.GEMINI), result.log.switchedTo)
    assertTrue(seen.any { it is AssistantState.SwitchingProvider && it.reason == SwitchReason.TOOL_USE_FAILED })
  }

  @Test
  fun `col servizio fissato un tool_use_failed che si ripete fallisce con il provider e il perche'`() = runBlocking {
    val groq = FoProvider(ProviderId.GROQ, listOf(FoTurn.Fail(toolUseFailed()), FoTurn.Fail(toolUseFailed())))
    val gemini = FoProvider(ProviderId.GEMINI, listOf(FoTurn.Text("mai")))
    val error = failure { orchestrator.ask(input(ready(groq), ready(gemini), pin = true), MutableStateFlow(AssistantState.Idle)) }
    assertEquals(FailureKind.PROVIDER, error.kind)
    assertEquals(ProviderId.GROQ, error.provider)
    assertEquals(SwitchReason.TOOL_USE_FAILED, error.reason)
    assertEquals(400, error.error?.httpCode)
    assertTrue(gemini.streamed.isEmpty())
  }

  @Test
  fun `una risposta vuota per fine dei token si riprova con piu' token, poi si cambia, poi si fallisce, mai DONE`() = runBlocking {
    val groq = FoProvider(ProviderId.GROQ, listOf(FoTurn.Empty(FinishReason.LENGTH), FoTurn.Empty(FinishReason.LENGTH)))
    val gemini = FoProvider(ProviderId.GEMINI, listOf(FoTurn.Empty(FinishReason.LENGTH), FoTurn.Empty(FinishReason.OTHER)))
    val conversation = Conversation(1L, 0L)
    val error = failure { orchestrator.ask(input(ready(groq), ready(gemini), conversation = conversation), MutableStateFlow(AssistantState.Idle)) }

    assertEquals(FailureKind.PROVIDER, error.kind)
    assertEquals(SwitchReason.EMPTY_ANSWER, error.reason)
    assertEquals(ProviderId.GEMINI, error.provider)
    assertEquals(listOf(ProviderSwitch(ProviderId.GROQ, ProviderId.GEMINI, SwitchReason.EMPTY_ANSWER)), error.switches)
    // La riprova ha il triplo del tetto di uscita; la riserva riparte dal tetto del giro.
    assertEquals(listOf(1_500, 4_500), groq.streamed.map { it.maxOutputTokens })
    assertEquals(listOf(1_500, 4_500), gemini.streamed.map { it.maxOutputTokens })
    // Niente di vuoto e' finito nella conversazione come uno scambio riuscito.
    assertTrue(conversation.exchanges.isEmpty())
  }

  @Test
  fun `se il modello non puo' avere piu' token, la riprova pensa meno`() = runBlocking {
    val capped = ModelCatalogue(chat = listOf(ModelInfo("modello-groq", "modello-groq", ModelKind.CHAT, maxOutputTokens = 1_500)), stt = emptyList())
    val groq = FoProvider(ProviderId.GROQ, listOf(FoTurn.Empty(FinishReason.LENGTH), FoTurn.Text("Sereno.")))
    val result = orchestrator.ask(input(ready(groq, capped)), MutableStateFlow(AssistantState.Idle))
    assertEquals("Sereno.", result.answer)
    assertEquals(listOf(ReasoningLevel.MEDIUM, ReasoningLevel.LOW), groq.streamed.map { it.reasoning })
    assertEquals(listOf(1_500, 1_500), groq.streamed.map { it.maxOutputTokens })
  }

  @Test
  fun `una chiamata malformata vuota si riprova identica, e col servizio fissato poi si fallisce`() = runBlocking {
    val groq = FoProvider(ProviderId.GROQ, listOf(FoTurn.Empty(FinishReason.OTHER), FoTurn.Empty(FinishReason.OTHER)))
    val gemini = FoProvider(ProviderId.GEMINI, listOf(FoTurn.Text("mai")))
    val error = failure { orchestrator.ask(input(ready(groq), ready(gemini), pin = true), MutableStateFlow(AssistantState.Idle)) }
    assertEquals(FailureKind.PROVIDER, error.kind)
    assertEquals(SwitchReason.EMPTY_ANSWER, error.reason)
    assertEquals(groq.streamed[0], groq.streamed[1])
    assertTrue(gemini.streamed.isEmpty())
  }

  @Test
  fun `una richiesta troppo lunga si accorcia una volta, storia a meta' e risultati dei tool piu' corti`() = runBlocking {
    val conversation = Conversation(1L, 0L)
    repeat(4) { i -> conversation.exchanges += Exchange("domanda $i", "risposta $i " + "y".repeat(700), emptyList(), ProviderId.GROQ, i.toLong()) }
    val call = ToolCall("old_1", "lungo", JsonObject(emptyMap()))
    conversation.lastToolRound = listOf(Message.Assistant(null, listOf(call)), Message.ToolResult("old_1", "lungo", "z".repeat(3_000)))
    val groq = FoProvider(ProviderId.GROQ, listOf(FoTurn.Calls(listOf("lungo")), FoTurn.Fail(tooLong()), FoTurn.Text("Sereno.")))
    val gemini = FoProvider(ProviderId.GEMINI, listOf(FoTurn.Text("mai")))
    val result = orchestrator.ask(input(ready(groq), ready(gemini), conversation = conversation), MutableStateFlow(AssistantState.Idle))

    assertEquals("Sereno.", result.answer)
    assertTrue(result.log.switches.isEmpty())
    assertTrue(gemini.streamed.isEmpty())
    val before = groq.streamed[1].messages
    val after = groq.streamed[2].messages
    // Il traffico tool della domanda di prima c'era, e dopo l'accorciamento non c'e' piu'.
    assertTrue(before.any { it is Message.ToolResult && it.callId == "old_1" })
    assertTrue(after.none { it is Message.ToolResult && it.callId == "old_1" })
    // Il risultato del tool di questa domanda c'e' ancora, a un terzo del tetto.
    val longBefore = before.filterIsInstance<Message.ToolResult>().single { it.callId == "call_0" }.content
    val longAfter = after.filterIsInstance<Message.ToolResult>().single { it.callId == "call_0" }.content
    assertTrue(longBefore.length > 2_000)
    assertTrue("accorciato a ${longAfter.length}", longAfter.length < 900)
    // E la coppia chiamata/risultato resta intera: un risultato orfano sarebbe un 400.
    assertTrue(after.any { it is Message.Assistant && it.toolCalls.any { c -> c.id == "call_0" } })
    assertTrue(HistoryCompactor.estimateTokens(after) < HistoryCompactor.estimateTokens(before))
    // La domanda dell'utente resta intera.
    assertTrue(after.any { it is Message.User && it.text == "che tempo fa?" })
  }

  @Test
  fun `troppo lunga anche accorciata passa alla riserva, e fissata fallisce con CONTEXT_TOO_LONG`() = runBlocking {
    val groq = FoProvider(ProviderId.GROQ, listOf(FoTurn.Fail(tooLong()), FoTurn.Fail(tooLong())))
    val gemini = FoProvider(ProviderId.GEMINI, listOf(FoTurn.Text("Da Gemini.")))
    val result = orchestrator.ask(input(ready(groq), ready(gemini)), MutableStateFlow(AssistantState.Idle))
    assertEquals("Da Gemini.", result.answer)
    assertEquals(listOf(ProviderSwitch(ProviderId.GROQ, ProviderId.GEMINI, SwitchReason.CONTEXT_TOO_LONG)), result.log.switches)

    val pinned = FoProvider(ProviderId.GROQ, listOf(FoTurn.Fail(tooLong()), FoTurn.Fail(tooLong())))
    val error = failure { orchestrator.ask(input(ready(pinned), ready(gemini), pin = true), MutableStateFlow(AssistantState.Idle)) }
    assertEquals(FailureKind.CONTEXT_TOO_LONG, error.kind)
    assertEquals(2, pinned.streamed.size)
  }

  @Test
  fun `un modello sparito si dice all'app, e la domanda passa alla riserva`() = runBlocking {
    val reported = mutableListOf<Pair<ProviderId, String>>()
    val groq = FoProvider(ProviderId.GROQ, listOf(FoTurn.Fail(modelGone())))
    val gemini = FoProvider(ProviderId.GEMINI, listOf(FoTurn.Text("Da Gemini.")))
    val result = orchestrator.ask(input(ready(groq), ready(gemini), onModelUnavailable = { p, m -> reported += p to m }), MutableStateFlow(AssistantState.Idle))
    assertEquals("Da Gemini.", result.answer)
    assertEquals(listOf(ProviderId.GROQ to "modello-groq"), reported)
    assertEquals(listOf(ProviderSwitch(ProviderId.GROQ, ProviderId.GEMINI, SwitchReason.MODEL_UNAVAILABLE)), result.log.switches)
    // Una volta sola: un modello sparito non torna riprovando.
    assertEquals(1, groq.streamed.size)
  }

  @Test
  fun `col servizio fissato un modello sparito fallisce con MODEL_UNAVAILABLE, e l'app lo sa lo stesso`() = runBlocking {
    val reported = mutableListOf<Pair<ProviderId, String>>()
    val groq = FoProvider(ProviderId.GROQ, listOf(FoTurn.Fail(modelGone())))
    val gemini = FoProvider(ProviderId.GEMINI, listOf(FoTurn.Text("mai")))
    val error = failure {
      orchestrator.ask(input(ready(groq), ready(gemini), pin = true, onModelUnavailable = { p, m -> reported += p to m }), MutableStateFlow(AssistantState.Idle))
    }
    assertEquals(FailureKind.MODEL_UNAVAILABLE, error.kind)
    assertEquals(SwitchReason.MODEL_UNAVAILABLE, error.reason)
    assertEquals(listOf(ProviderId.GROQ to "modello-groq"), reported)
    assertTrue(gemini.streamed.isEmpty())
  }

  @Test
  fun `il modello del router sparito si dice all'app, ma non sposta la domanda`() = runBlocking {
    val reported = mutableListOf<Pair<ProviderId, String>>()
    val groq = FoProvider(ProviderId.GROQ, listOf(FoTurn.Text("Sereno.")), classifierError = modelGone())
    val gemini = FoProvider(ProviderId.GEMINI, listOf(FoTurn.Text("mai")))
    val result = orchestrator.ask(
      input(ready(groq), ready(gemini), preselected = null, onModelUnavailable = { p, m -> reported += p to m }),
      MutableStateFlow(AssistantState.Idle),
    )
    assertEquals("Sereno.", result.answer)
    assertEquals(ProviderId.GROQ, result.provider)
    assertEquals(listOf(ProviderId.GROQ to "piccolo"), reported)
    assertTrue(result.log.switches.isEmpty())
    assertEquals(1, groq.routed.size)
    assertTrue(gemini.streamed.isEmpty() && gemini.routed.isEmpty())
  }

  @Test
  fun `il giro chiede il timeout lungo quando il modello pensa`() = runBlocking {
    val groq = FoProvider(ProviderId.GROQ, listOf(FoTurn.Text("Sereno.")))
    orchestrator.ask(input(ready(groq)), MutableStateFlow(AssistantState.Idle))
    // Il ragionamento di default e' MEDIUM e il budget di default ha spazio: i centoventi secondi pieni.
    assertEquals(ReasoningLevel.MEDIUM, groq.streamed.single().reasoning)
    assertEquals(120_000, groq.streamed.single().readTimeoutMillis)
  }

  @Test
  fun `lo stato Failed e il Done portano provider, perche' e cambi, con default che non rompono nessuno`() {
    val failed = AssistantState.Failed("?", FailureKind.PROVIDER, null, null, null)
    assertEquals(null, failed.provider)
    assertEquals(null, failed.reason)
    val switching = AssistantState.SwitchingProvider("?", ProviderId.GROQ, ProviderId.GEMINI)
    assertEquals(null, switching.reason)
    val done = AssistantState.Done("?", "ok", emptyList(), ProviderId.GEMINI, AskMode.TEXT, null, emptyList(), 0L)
    assertTrue(done.switches.isEmpty())
  }
}
