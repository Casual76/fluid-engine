package dev.antigravity.fluidengine.ai.orchestrator

import dev.antigravity.fluidengine.ai.keys.AiSettings
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
import dev.antigravity.fluidengine.ai.provider.TranscribeOptions
import dev.antigravity.fluidengine.ai.provider.Transcript
import dev.antigravity.fluidengine.ai.tools.AiTool
import dev.antigravity.fluidengine.ai.tools.AiToolGroup
import dev.antigravity.fluidengine.ai.tools.Schema
import dev.antigravity.fluidengine.ai.tools.ToolOutput
import dev.antigravity.fluidengine.ai.tools.ToolRegistry
import java.io.File
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.JsonObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

private enum class DtGroup(override val id: String, override val statusKey: String, override val hint: String) : AiToolGroup {
  DOCS("documenti", "docs", "allegati e testi lunghi"),
}

private class DtCtx

private sealed interface DtTurn {
  data class Calls(val names: List<String>) : DtTurn
  data class Text(val text: String) : DtTurn
}

private class DtProvider(
  override val id: ProviderId,
  turns: List<DtTurn>,
  private val classifier: String = """{"gruppi":["documenti"],"profondo":false}""",
) : ChatProvider {
  private val queue = ArrayDeque(turns)
  val streamed = mutableListOf<ChatRequest>()

  override suspend fun complete(request: ChatRequest): ChatTurn =
    ChatTurn(Message.Assistant(classifier), FinishReason.STOP, null, RateLimitInfo.EMPTY)

  override fun stream(request: ChatRequest): Flow<ChatDelta> = flow {
    streamed += request.copy(messages = request.messages.toList())
    when (val turn = queue.removeFirstOrNull() ?: DtTurn.Text("(fine copione)")) {
      is DtTurn.Text -> {
        emit(ChatDelta.Text(turn.text))
        emit(ChatDelta.Finish(FinishReason.STOP, null, RateLimitInfo.EMPTY))
      }
      is DtTurn.Calls -> {
        turn.names.forEachIndexed { index, name -> emit(ChatDelta.ToolCallPart(index, "call_$index", name, "{}")) }
        emit(ChatDelta.Finish(FinishReason.TOOL_CALLS, null, RateLimitInfo.EMPTY))
      }
    }
  }

  override suspend fun listModels(): ModelCatalogue = ModelCatalogue(emptyList(), emptyList())
  override suspend fun transcribe(audio: File, mime: String, options: TranscribeOptions): Transcript = Transcript("", null)
}

/** Un tool che porta un PDF finto: la materia dell'escalation al livello profondo. */
private class DtAttachmentTool : AiTool<DtCtx> {
  override val name = "allegato"
  override val group: AiToolGroup = DtGroup.DOCS
  override val description = "porta un pdf"
  override val parameters: JsonObject = Schema.obj(emptyMap())
  override suspend fun run(args: JsonObject, ctx: DtCtx): ToolOutput =
    ToolOutput("allegato: circolare.pdf", listOf(ContentPart.Document(byteArrayOf(1, 2, 3), "application/pdf", "circolare.pdf")))
}

/** Un tool che porta tanto testo: oltre `escalationChars` il giro passerebbe al profondo. */
private class DtLongTool : AiTool<DtCtx> {
  override val name = "lungo"
  override val group: AiToolGroup = DtGroup.DOCS
  override val description = "tante righe"
  override val parameters: JsonObject = Schema.obj(emptyMap())
  override suspend fun run(args: JsonObject, ctx: DtCtx): ToolOutput = ToolOutput((1..60).joinToString("\n") { "riga $it" })
}

/** Un tool che chiede lui stesso di passare al livello piu' capace. */
private class DtEscalatingTool : AiTool<DtCtx> {
  override val name = "pesante"
  override val group: AiToolGroup = DtGroup.DOCS
  override val description = "porta roba pesante"
  override val parameters: JsonObject = Schema.obj(emptyMap())
  override suspend fun run(args: JsonObject, ctx: DtCtx): ToolOutput = ToolOutput("molti dati", escalate = true)
}

/**
 * Il livello profondo spento da configurazione (3.0.0). Prima un'app che non lo voleva toglieva il
 * modello profondo da ogni provider (`copy(deepModel = null)`) a ogni domanda; ora e'
 * `AiOrchestratorConfig(deepTierEnabled = false)`, e in nessun modo una domanda esce dalla chat.
 */
class AiDeepTierTest {

  private val registry = ToolRegistry(listOf(DtAttachmentTool(), DtLongTool(), DtEscalatingTool()), DtGroup.entries)
  private val router = AiRouter(DtGroup.entries, null, "un test", defaultGroups = listOf(DtGroup.DOCS))

  private fun ready(provider: DtProvider) =
    ReadyProvider(provider, "chat-${provider.id.id}", "stt", "router", deepModel = "profondo-${provider.id.id}")

  private fun ask(provider: DtProvider, config: AiOrchestratorConfig, deepRequested: Boolean = false): AskResult = runBlocking {
    val input = AskInput(
      question = "cosa dice la circolare?",
      mode = AskMode.TEXT,
      language = "it",
      settings = AiSettings(),
      providers = listOf(ready(provider)),
      toolContext = DtCtx(),
      systemPrompt = "sei un assistente",
      conversation = Conversation(1L, 0L),
      actionsEnabled = false,
      deepRequested = deepRequested,
    )
    AiOrchestrator(registry, router, AiDiagnosticsLog(), config = config).ask(input, MutableStateFlow(AssistantState.Idle))
  }

  private val on = AiOrchestratorConfig()
  private val off = AiOrchestratorConfig(deepTierEnabled = false)

  @Test
  fun `di serie il livello profondo e' acceso`() {
    assertTrue(on.deepTierEnabled)
    // Controllo: il router che dice profondo porta il giro sul modello profondo, come sempre.
    val provider = DtProvider(ProviderId.GROQ, listOf(DtTurn.Text("Ecco.")), classifier = """{"gruppi":["documenti"],"profondo":true}""")
    val result = ask(provider, on)
    assertEquals("profondo-groq", provider.streamed.single().model)
    assertEquals(ModelTier.DEEP, result.tierReached)
  }

  @Test
  fun `spento, il router che dice profondo non sposta la domanda dalla chat`() {
    val provider = DtProvider(ProviderId.GROQ, listOf(DtTurn.Text("Ecco.")), classifier = """{"gruppi":["documenti"],"profondo":true}""")
    val result = ask(provider, off)
    assertEquals("chat-groq", provider.streamed.single().model)
    assertEquals(ModelTier.CHAT, result.tierReached)
    assertTrue(result.log.modelsUsed.none { it.tier == ModelTier.DEEP })
    assertNull(result.log.models[ModelTier.DEEP])
  }

  @Test
  fun `spento, nemmeno l'app puo' chiedere il profondo`() {
    val provider = DtProvider(ProviderId.GROQ, listOf(DtTurn.Text("Ecco.")))
    val result = ask(provider, off, deepRequested = true)
    assertEquals("chat-groq", provider.streamed.single().model)
    assertEquals(ModelTier.CHAT, result.tierReached)
    // E con il profondo acceso, la stessa richiesta lo usa.
    val control = DtProvider(ProviderId.GROQ, listOf(DtTurn.Text("Ecco.")))
    assertEquals(ModelTier.DEEP, ask(control, on, deepRequested = true).tierReached)
  }

  @Test
  fun `spento, il tool modello_avanzato non viene offerto al modello`() {
    val withDeep = DtProvider(ProviderId.GROQ, listOf(DtTurn.Text("Ecco.")))
    ask(withDeep, on)
    assertTrue(withDeep.streamed.single().tools.any { it.name == ToolRegistry.DEEP_MODEL })

    val withoutDeep = DtProvider(ProviderId.GROQ, listOf(DtTurn.Text("Ecco.")))
    ask(withoutDeep, off)
    assertTrue(withoutDeep.streamed.single().tools.none { it.name == ToolRegistry.DEEP_MODEL })
  }

  @Test
  fun `spento, molto testo dai tool non porta al profondo`() {
    val small = AiOrchestratorConfig(escalationChars = 50, deepTierEnabled = false)
    val provider = DtProvider(ProviderId.GROQ, listOf(DtTurn.Calls(listOf("lungo")), DtTurn.Text("Fatto.")))
    val result = ask(provider, small)
    assertEquals(listOf("chat-groq", "chat-groq"), provider.streamed.map { it.model })
    assertEquals(ModelTier.CHAT, result.tierReached)

    // Controllo: acceso, la stessa soglia porta il secondo giro sul profondo.
    val control = DtProvider(ProviderId.GROQ, listOf(DtTurn.Calls(listOf("lungo")), DtTurn.Text("Fatto.")))
    ask(control, AiOrchestratorConfig(escalationChars = 50))
    assertEquals(listOf("chat-groq", "profondo-groq"), control.streamed.map { it.model })
  }

  @Test
  fun `spento, un tool che chiede l'escalation non cambia modello`() {
    val provider = DtProvider(ProviderId.GROQ, listOf(DtTurn.Calls(listOf("pesante")), DtTurn.Text("Fatto.")))
    val result = ask(provider, off)
    assertEquals(listOf("chat-groq", "chat-groq"), provider.streamed.map { it.model })
    assertEquals(ModelTier.CHAT, result.tierReached)
  }

  @Test
  fun `spento, un allegato che il modello della chat regge entra come parte senza cambiare livello`() {
    // Gemini legge i documenti con qualunque modello: acceso, l'allegato porta al profondo; spento resta sulla chat.
    val provider = DtProvider(ProviderId.GEMINI, listOf(DtTurn.Calls(listOf("allegato")), DtTurn.Text("La gita e' il 12.")))
    val result = ask(provider, off)
    assertEquals("La gita e' il 12.", result.answer)
    assertEquals(listOf("chat-gemini", "chat-gemini"), provider.streamed.map { it.model })
    val withParts = provider.streamed[1].messages.filterIsInstance<Message.User>().last()
    assertTrue("l'allegato arriva lo stesso, come parte del messaggio", withParts.hasBinaryParts)
    assertEquals(ModelTier.CHAT, result.tierReached)

    val control = DtProvider(ProviderId.GEMINI, listOf(DtTurn.Calls(listOf("allegato")), DtTurn.Text("La gita e' il 12.")))
    assertEquals(ModelTier.DEEP, ask(control, on).tierReached)
  }

  @Test
  fun `spento, i provider dell'app non vengono ritoccati e il modello profondo salvato semplicemente non conta`() {
    val provider = DtProvider(ProviderId.GROQ, listOf(DtTurn.Text("Ecco.")))
    val withDeepModel = ready(provider)
    assertEquals("profondo-groq", withDeepModel.deepModel)
    val result = runBlocking {
      val input = AskInput(
        question = "?", mode = AskMode.TEXT, language = "it", settings = AiSettings(), providers = listOf(withDeepModel),
        toolContext = DtCtx(), systemPrompt = "x", conversation = Conversation(1L, 0L), actionsEnabled = false,
      )
      AiOrchestrator(registry, router, AiDiagnosticsLog(), config = off).ask(input, MutableStateFlow(AssistantState.Idle))
    }
    assertEquals(ModelTier.CHAT, result.tierReached)
    assertEquals("l'oggetto dell'app e' intatto", "profondo-groq", withDeepModel.deepModel)
    assertFalse(provider.streamed.any { it.model.startsWith("profondo") })
  }
}
