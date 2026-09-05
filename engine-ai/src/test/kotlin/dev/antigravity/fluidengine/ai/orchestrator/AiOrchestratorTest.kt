package dev.antigravity.fluidengine.ai.orchestrator

import dev.antigravity.fluidengine.ai.keys.AiSettings
import dev.antigravity.fluidengine.ai.net.AiError
import dev.antigravity.fluidengine.ai.net.RateLimitInfo
import dev.antigravity.fluidengine.ai.provider.ChatDelta
import dev.antigravity.fluidengine.ai.provider.ChatProvider
import dev.antigravity.fluidengine.ai.provider.ChatRequest
import dev.antigravity.fluidengine.ai.provider.ChatTurn
import dev.antigravity.fluidengine.ai.provider.ContentPart
import dev.antigravity.fluidengine.ai.provider.FinishReason
import dev.antigravity.fluidengine.ai.provider.Message
import dev.antigravity.fluidengine.ai.provider.ModelCatalogue
import dev.antigravity.fluidengine.ai.provider.ModelTier
import dev.antigravity.fluidengine.ai.provider.ProviderId
import dev.antigravity.fluidengine.ai.provider.ReadyProvider
import dev.antigravity.fluidengine.ai.provider.ToolChoice
import dev.antigravity.fluidengine.ai.provider.TranscribeOptions
import dev.antigravity.fluidengine.ai.provider.Transcript
import dev.antigravity.fluidengine.ai.tools.AiTool
import dev.antigravity.fluidengine.ai.tools.AiToolGroup
import dev.antigravity.fluidengine.ai.tools.Schema
import dev.antigravity.fluidengine.ai.tools.ToolOutput
import dev.antigravity.fluidengine.ai.tools.ToolRegistry
import java.io.File
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** I gruppi di un'app finta: l'engine non li conosce, il test si'. */
private enum class G(override val id: String, override val statusKey: String, override val hint: String) : AiToolGroup {
  HOURLY("orario", "hourly", "ora per ora"),
  SKY("cielo", "sky", "sole e luna"),
  DOCS("documenti", "docs", "allegati"),
  APP("app", "app", "azioni nell'app"),
}

/** Il contesto dei tool dell'app finta: qui non serve niente. */
private class Ctx

/** Un turno recitato: testo, tool call, un errore da lanciare prima di rispondere, o un flusso che si spezza. */
private sealed interface Scripted {
  data class Text(val text: String) : Scripted
  data class Calls(val calls: List<Pair<String, JsonObject>>, val preamble: String? = null) : Scripted
  data class Fail(val error: Throwable) : Scripted
  data class Broken(val prefix: String, val error: Throwable) : Scripted
}

private class FakeProvider(
  override val id: ProviderId,
  turns: List<Scripted>,
  private val classifier: String = """{"gruppi":["orario"],"profondo":false}""",
  completes: List<String> = emptyList(),
) : ChatProvider {
  private val queue = ArrayDeque(turns)
  private val completeQueue = ArrayDeque(completes)
  val requests = mutableListOf<ChatRequest>()
  val streamed = mutableListOf<ChatRequest>()

  override suspend fun complete(request: ChatRequest): ChatTurn {
    requests += request
    if (request.jsonSchema != null) {
      return ChatTurn(Message.Assistant(classifier), FinishReason.STOP, null, RateLimitInfo.EMPTY)
    }
    val text = completeQueue.removeFirstOrNull() ?: error("complete() non previsto nel giro dei tool")
    return ChatTurn(Message.Assistant(text), FinishReason.STOP, null, RateLimitInfo.EMPTY)
  }

  override fun stream(request: ChatRequest): Flow<ChatDelta> = flow {
    streamed += request
    when (val turn = queue.removeFirstOrNull() ?: Scripted.Text("(fine copione)")) {
      is Scripted.Fail -> throw turn.error
      is Scripted.Broken -> {
        emit(ChatDelta.Text(turn.prefix))
        throw turn.error
      }
      is Scripted.Text -> {
        turn.text.chunked(7).forEach { emit(ChatDelta.Text(it)); delay(5) }
        emit(ChatDelta.Finish(FinishReason.STOP, null, RateLimitInfo.EMPTY))
      }
      is Scripted.Calls -> {
        turn.preamble?.let { emit(ChatDelta.Text(it)) }
        turn.calls.forEachIndexed { index, (name, args) ->
          emit(ChatDelta.ToolCallPart(index, "call_$index", name, null))
          emit(ChatDelta.ToolCallPart(index, null, null, args.toString()))
        }
        emit(ChatDelta.Finish(FinishReason.TOOL_CALLS, null, RateLimitInfo.EMPTY))
      }
    }
  }

  override suspend fun listModels(): ModelCatalogue = ModelCatalogue(emptyList(), emptyList())
  override suspend fun transcribe(audio: File, mime: String, options: TranscribeOptions): Transcript = Transcript("", null)
}

private class EchoTool(override val name: String, override val group: AiToolGroup, private val slowMillis: Long = 0) : AiTool<Ctx> {
  override val description = "eco"
  override val parameters = Schema.obj(mapOf("x" to Schema.str("x")))
  val calls = mutableListOf<JsonObject>()
  override suspend fun run(args: JsonObject, ctx: Ctx): ToolOutput {
    calls += args
    if (slowMillis > 0) delay(slowMillis)
    return ToolOutput("$name ha ricevuto ${args["x"]?.let { (it as JsonPrimitive).content } ?: "-"}")
  }
}

/** Un tool che porta un allegato: un PDF finto di pochi byte. */
private class AttachmentTool : AiTool<Ctx> {
  override val name = "allegato"
  override val group: AiToolGroup = G.DOCS
  override val description = "porta un pdf"
  override val parameters = Schema.obj(emptyMap())
  override suspend fun run(args: JsonObject, ctx: Ctx): ToolOutput =
    ToolOutput("allegato: circolare.pdf (1 pagina)", listOf(ContentPart.Document(byteArrayOf(1, 2, 3), "application/pdf", "circolare.pdf")))
}

class AiOrchestratorTest {

  private val now = EchoTool("adesso", G.HOURLY)
  private val sun = EchoTool("sole", G.SKY)
  private val attachment = AttachmentTool()
  private val registry = ToolRegistry(listOf(now, sun, attachment), G.entries, actionGroup = G.APP)
  private val router = AiRouter(G.entries, G.APP, "un test", defaultGroups = listOf(G.HOURLY))
  private val diagnostics = AiDiagnosticsLog()

  private fun orchestrator(maxRounds: Int = 6) =
    AiOrchestrator(registry, router, diagnostics, config = AiOrchestratorConfig(maxRounds = maxRounds))

  private fun ready(provider: FakeProvider) =
    ReadyProvider(provider, "modello-${provider.id.id}", "stt", "piccolo", deepModel = "profondo-${provider.id.id}")

  private fun input(
    question: String,
    vararg providers: FakeProvider,
    conversation: Conversation = Conversation(1L, 0L),
    preselected: Set<AiToolGroup>? = null,
    hint: Set<AiToolGroup> = emptySet(),
    fallback: suspend (ContentPart) -> String? = { null },
    actions: Boolean = false,
  ) = AskInput(
    question = question,
    mode = AskMode.TEXT,
    language = "it",
    settings = AiSettings(),
    providers = providers.map { ready(it) },
    toolContext = Ctx(),
    systemPrompt = "sei un assistente",
    conversation = conversation,
    actionsEnabled = actions,
    preselectedGroups = preselected,
    routerHint = hint,
    attachmentFallback = fallback,
  )

  private fun args(x: String) = buildJsonObject { put("x", JsonPrimitive(x)) }

  @Test
  fun `due giri con chiamate parallele, poi la risposta in streaming, con gli stati in ordine`() = runBlocking {
    val groq = FakeProvider(
      ProviderId.GROQ,
      listOf(
        Scripted.Calls(listOf("adesso" to args("a"), "sole" to args("b")), preamble = "Controllo"),
        Scripted.Text("Piove alle 18. [[radar]]"),
      ),
      classifier = """{"gruppi":["orario","cielo"],"profondo":false}""",
    )
    val states = mutableListOf<AssistantState>()
    val state = MutableStateFlow<AssistantState>(AssistantState.Idle)
    val collector = launch(Dispatchers.Unconfined) { state.collect { states += it } }
    val result = orchestrator().ask(input("piove?", groq), state)
    collector.cancel()

    assertEquals("Piove alle 18.", result.answer)
    assertEquals(listOf(AnswerChip("radar")), result.chips)
    assertEquals(listOf("adesso", "sole"), result.toolsUsed)
    assertEquals(listOf(args("a")), now.calls)
    assertEquals(listOf(args("b")), sun.calls)
    assertEquals(ModelTier.CHAT, result.tierReached)
    // Lo stadio 1 e' andato sul modello piccolo con lo schema; il giro dei tool aveva i tool dei due gruppi.
    assertEquals("piccolo", groq.requests.single().model)
    assertTrue(groq.streamed.first().tools.map { it.name }.containsAll(listOf("adesso", "sole", ToolRegistry.MORE_TOOLS)))
    // Il secondo giro porta la risposta dei tool nell'ordine delle chiamate.
    val second = groq.streamed[1].messages
    val results = second.filterIsInstance<Message.ToolResult>()
    assertEquals(listOf("call_0", "call_1"), results.map { it.callId })
    assertTrue(results[0].content.startsWith("adesso ha ricevuto a"))
    // Il preambolo prima delle tool call non e' mai diventato una risposta.
    assertTrue(states.none { it is AssistantState.Answering && it.partial == "Controllo" })
    assertTrue(states.any { it is AssistantState.Classifying })
    assertTrue(states.any { it is AssistantState.Working && it.statusKey == G.HOURLY.statusKey && it.statusExtra == 1 })
    assertTrue(states.any { it is AssistantState.Answering })
    assertEquals(1, diagnostics.entries.value.size)
    assertEquals(listOf("orario", "cielo"), diagnostics.entries.value.first().groups)
    assertEquals(mapOf(ModelTier.ROUTER to "piccolo", ModelTier.CHAT to "modello-groq"), diagnostics.entries.value.first().models)
  }

  @Test
  fun `il pre-router dell'app salta lo stadio 1, il suggerimento arriva al router`() = runBlocking {
    val groq = FakeProvider(ProviderId.GROQ, listOf(Scripted.Text("Sereno.")))
    val state = MutableStateFlow<AssistantState>(AssistantState.Idle)
    orchestrator().ask(input("?", groq, preselected = setOf(G.SKY)), state)
    assertTrue(groq.requests.isEmpty())
    assertTrue(groq.streamed.single().tools.any { it.name == "sole" })
    assertTrue(groq.streamed.single().tools.none { it.name == "adesso" })

    val hinted = FakeProvider(ProviderId.GROQ, listOf(Scripted.Text("Sereno.")))
    orchestrator().ask(input("?", hinted, hint = setOf(G.SKY)), state)
    val routerMessage = hinted.requests.single().messages.filterIsInstance<Message.User>().single().text
    assertTrue(routerMessage.contains("Gruppi probabili: cielo"))
  }

  @Test
  fun `il router che dice profondo fa partire il giro sul modello profondo`() = runBlocking {
    val groq = FakeProvider(ProviderId.GROQ, listOf(Scripted.Text("Ecco.")), classifier = """{"gruppi":["documenti"],"profondo":true}""")
    val state = MutableStateFlow<AssistantState>(AssistantState.Idle)
    val result = orchestrator().ask(input("cosa dice la circolare?", groq), state)
    assertEquals("profondo-groq", groq.streamed.single().model)
    assertEquals(ModelTier.DEEP, result.tierReached)
  }

  @Test
  fun `un allegato che il modello profondo regge entra come parte e alza il livello`() = runBlocking {
    val gemini = FakeProvider(
      ProviderId.GEMINI,
      listOf(Scripted.Calls(listOf("allegato" to JsonObject(emptyMap()))), Scripted.Text("La gita e' il 12.")),
      classifier = """{"gruppi":["documenti"],"profondo":false}""",
    )
    val state = MutableStateFlow<AssistantState>(AssistantState.Idle)
    val result = orchestrator().ask(input("cosa dice l'allegato?", gemini), state)
    assertEquals("La gita e' il 12.", result.answer)
    assertEquals("modello-gemini", gemini.streamed[0].model)
    assertEquals("profondo-gemini", gemini.streamed[1].model)
    val withParts = gemini.streamed[1].messages.filterIsInstance<Message.User>().last()
    assertTrue(withParts.hasBinaryParts)
    assertTrue(withParts.text.contains("circolare.pdf"))
    assertEquals(ModelTier.DEEP, result.tierReached)
    assertEquals("profondo-gemini", diagnostics.entries.value.first().models[ModelTier.DEEP])
  }

  @Test
  fun `un allegato che il modello non regge passa dalla traduzione dell'app`() = runBlocking {
    val groq = FakeProvider(
      ProviderId.GROQ,
      listOf(Scripted.Calls(listOf("allegato" to JsonObject(emptyMap()))), Scripted.Text("Dice che la gita e' il 12.")),
      classifier = """{"gruppi":["documenti"],"profondo":false}""",
    )
    val state = MutableStateFlow<AssistantState>(AssistantState.Idle)
    val result = orchestrator().ask(input("?", groq, fallback = { "TESTO ESTRATTO: gita il 12" }), state)
    assertEquals("Dice che la gita e' il 12.", result.answer)
    val user = groq.streamed[1].messages.filterIsInstance<Message.User>().last()
    assertFalse(user.hasBinaryParts)
    assertTrue(user.text.contains("TESTO ESTRATTO"))
    assertEquals(ModelTier.CHAT, result.tierReached)
  }

  @Test
  fun `un flusso che si spezza dopo del testo si rifa' senza stream`() = runBlocking {
    val groq = FakeProvider(
      ProviderId.GROQ,
      listOf(Scripted.Broken("Piove al", AiError.Network("connessione caduta"))),
      completes = listOf("Piove alle 18, poi smette."),
    )
    val state = MutableStateFlow<AssistantState>(AssistantState.Idle)
    val result = orchestrator().ask(input("piove?", groq), state)
    assertEquals("Piove alle 18, poi smette.", result.answer)
    // Una richiesta senza schema: la riprova non-stream, con lo stesso modello del giro.
    val retry = groq.requests.single { it.jsonSchema == null }
    assertEquals("modello-groq", retry.model)
    assertEquals(1, groq.streamed.size)
  }

  @Test
  fun `un flusso che si spezza prima di dire qualcosa segue il failover`() = runBlocking {
    val groq = FakeProvider(ProviderId.GROQ, listOf(Scripted.Fail(AiError.Network("giu'")), Scripted.Text("Ora va.")))
    val state = MutableStateFlow<AssistantState>(AssistantState.Idle)
    val result = orchestrator().ask(input("?", groq), state)
    assertEquals("Ora va.", result.answer)
    assertTrue(groq.requests.none { it.jsonSchema == null })
    assertEquals(2, groq.streamed.size)
  }

  @Test
  fun `un 429 su Groq passa a Gemini con la stessa conversazione`() = runBlocking {
    val groq = FakeProvider(ProviderId.GROQ, listOf(Scripted.Fail(AiError.RateLimited(3.0, RateLimitInfo.EMPTY, message = "429"))))
    val gemini = FakeProvider(ProviderId.GEMINI, listOf(Scripted.Text("Da Gemini: sereno.")))
    val state = MutableStateFlow<AssistantState>(AssistantState.Idle)
    val result = orchestrator().ask(input("che tempo fa?", groq, gemini), state)
    assertEquals("Da Gemini: sereno.", result.answer)
    assertEquals(ProviderId.GEMINI, result.provider)
    assertEquals("modello-gemini", gemini.streamed.single().model)
    assertEquals(listOf(ProviderId.GEMINI), result.log.switchedTo)
    assertTrue(gemini.streamed.single().messages.any { it is Message.User && it.text == "che tempo fa?" })
  }

  @Test
  fun `un 429 senza riserve aspetta il retry-after con il conto alla rovescia`() = runBlocking {
    val groq = FakeProvider(
      ProviderId.GROQ,
      listOf(Scripted.Fail(AiError.RateLimited(1.0, RateLimitInfo.EMPTY, message = "429")), Scripted.Text("Ora va.")),
    )
    val state = MutableStateFlow<AssistantState>(AssistantState.Idle)
    val seen = mutableListOf<AssistantState>()
    val collector = launch(Dispatchers.Unconfined) { state.collect { seen += it } }
    val result = orchestrator().ask(input("?", groq), state)
    collector.cancel()
    assertEquals("Ora va.", result.answer)
    assertTrue(seen.any { it is AssistantState.WaitingRateLimit && it.secondsLeft == 1 })
    assertEquals(1, result.log.waitedSeconds)
  }

  @Test
  fun `dopo sei giri di tool si forza la risposta senza strumenti`() = runBlocking {
    val loops = (1..5).map { Scripted.Calls(listOf("adesso" to args("$it"))) } + Scripted.Text("Basta cosi'.") + Scripted.Text("mai")
    val groq = FakeProvider(ProviderId.GROQ, loops)
    val state = MutableStateFlow<AssistantState>(AssistantState.Idle)
    val result = orchestrator(maxRounds = 6).ask(input("?", groq), state)
    // 5 giri con tool + il sesto forzato senza tool = 6 stream; il settimo copione non parte.
    assertEquals(6, groq.streamed.size)
    val last = groq.streamed.last()
    assertTrue(last.tools.isEmpty())
    assertEquals(ToolChoice.None, last.toolChoice)
    assertTrue(last.messages.last() is Message.System)
    assertEquals("Basta cosi'.", result.answer)
    assertEquals(5, now.calls.size)
  }

  @Test
  fun `una tool call al giro forzato si ignora e vale il testo`() = runBlocking {
    val loops = (1..5).map { Scripted.Calls(listOf("adesso" to args("$it"))) } + Scripted.Calls(listOf("adesso" to args("6")), preamble = "Riassumo.")
    val groq = FakeProvider(ProviderId.GROQ, loops)
    val state = MutableStateFlow<AssistantState>(AssistantState.Idle)
    val result = orchestrator(maxRounds = 6).ask(input("?", groq), state)
    assertEquals("Riassumo.", result.answer)
    assertEquals(5, now.calls.size)
  }

  @Test
  fun `altri_tool aggiunge un gruppo al giro dopo, ma mai le azioni se sono spente`() = runBlocking {
    val groq = FakeProvider(
      ProviderId.GROQ,
      listOf(
        Scripted.Calls(listOf(ToolRegistry.MORE_TOOLS to buildJsonObject { put("gruppo", JsonPrimitive("cielo")) })),
        Scripted.Calls(listOf(ToolRegistry.MORE_TOOLS to buildJsonObject { put("gruppo", JsonPrimitive("app")) })),
        Scripted.Calls(listOf("sole" to args("z"))),
        Scripted.Text("Tramonta alle 20."),
      ),
    )
    val state = MutableStateFlow<AssistantState>(AssistantState.Idle)
    val result = orchestrator().ask(input("quando tramonta?", groq), state)
    assertEquals("Tramonta alle 20.", result.answer)
    assertTrue(groq.streamed[0].tools.none { it.name == "sole" })
    assertTrue(groq.streamed[1].tools.any { it.name == "sole" })
    assertEquals(listOf(args("z")), sun.calls)
    assertEquals(listOf("orario", "cielo"), diagnostics.entries.value.first().groups)
  }

  @Test
  fun `la cancellazione a meta' stream non tocca la conversazione`() = runBlocking {
    val groq = FakeProvider(ProviderId.GROQ, listOf(Scripted.Text("Una risposta lunga che non finira' mai perche' verra' fermata prima.")))
    val state = MutableStateFlow<AssistantState>(AssistantState.Idle)
    val conversation = Conversation(1L, 0L)
    val job = launch {
      runCatching { orchestrator().ask(input("?", groq, conversation = conversation), state) }
    }
    while (state.value !is AssistantState.Answering) delay(2)
    job.cancel(CancellationException("stop"))
    job.join()
    assertTrue(conversation.exchanges.isEmpty())
  }

  @Test
  fun `senza provider la domanda fallisce con NO_KEYS`() = runBlocking {
    val state = MutableStateFlow<AssistantState>(AssistantState.Idle)
    val error = runCatching { orchestrator().ask(input("?"), state) }.exceptionOrNull()
    assertTrue(error is AssistantFailure && (error as AssistantFailure).kind == FailureKind.NO_KEYS)
  }

  @Test
  fun `su OpenRouter lo stadio 1 si salta e il catalogo parte intero`() = runBlocking {
    val openRouter = FakeProvider(ProviderId.OPENROUTER, listOf(Scripted.Text("Tutto sereno.")))
    val state = MutableStateFlow<AssistantState>(AssistantState.Idle)
    orchestrator().ask(input("?", openRouter), state)
    assertTrue(openRouter.requests.isEmpty())
    val tools = openRouter.streamed.single().tools.map { it.name }
    assertTrue(tools.containsAll(listOf("adesso", "sole", "allegato")))
    assertTrue(tools.none { it == ToolRegistry.MORE_TOOLS })
  }
}
