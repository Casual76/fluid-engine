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
import dev.antigravity.fluidengine.ai.provider.ToolCall
import dev.antigravity.fluidengine.ai.provider.TranscribeOptions
import dev.antigravity.fluidengine.ai.provider.Transcript
import dev.antigravity.fluidengine.ai.tools.AiTool
import dev.antigravity.fluidengine.ai.tools.AiToolGroup
import dev.antigravity.fluidengine.ai.tools.Schema
import dev.antigravity.fluidengine.ai.tools.ToolExecution
import dev.antigravity.fluidengine.ai.tools.ToolOutput
import dev.antigravity.fluidengine.ai.tools.ToolRegistry
import java.io.File
import java.util.Collections
import java.util.concurrent.atomic.AtomicInteger
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/*
 * L'identita' dell'operazione che l'orchestratore passa ai tool (3.0.6): una per chiamata, chiesta
 * all'app con AskInput.prepareToolExecution. I tool che implementano solo run(args, ctx) non se ne
 * accorgono.
 */

private enum class IdGroup(override val id: String, override val statusKey: String, override val hint: String) : AiToolGroup {
  MAIN("principale", "main", "tutto"),
}

/** Un tool nuovo: implementa l'overload con l'identita' e la annota. */
private class IdentityTool(override val name: String) : AiTool<Unit> {
  override val group: AiToolGroup = IdGroup.MAIN
  override val description = "annota l'identita'"
  override val parameters = Schema.obj(mapOf("x" to Schema.str("x")))
  val seen: MutableList<String> = Collections.synchronizedList(mutableListOf())
  var legacyCalls = 0
  override suspend fun run(args: JsonObject, ctx: Unit): ToolOutput {
    legacyCalls++
    return ToolOutput.error("non doveva passare di qui")
  }
  override suspend fun run(args: JsonObject, ctx: Unit, execution: ToolExecution): ToolOutput {
    seen += execution.operationId
    return ToolOutput("$name: ${execution.operationId}")
  }
}

/** Un tool di prima della 3.0.6: solo run(args, ctx). */
private class LegacyTool : AiTool<Unit> {
  override val name = "vecchio"
  override val group: AiToolGroup = IdGroup.MAIN
  override val description = "un tool di un'app che non e' stata toccata"
  override val parameters = Schema.obj(mapOf("x" to Schema.str("x")))
  var calls = 0
  override suspend fun run(args: JsonObject, ctx: Unit): ToolOutput {
    calls++
    return ToolOutput("vecchio ha risposto")
  }
}

private class IdChat(turns: List<List<String>>) : ChatProvider {
  override val id: ProviderId = ProviderId.GROQ
  private val queue = ArrayDeque(turns)
  val streamed = mutableListOf<ChatRequest>()
  override suspend fun complete(request: ChatRequest): ChatTurn =
    ChatTurn(Message.Assistant("""{"gruppi":["principale"],"profondo":false}"""), FinishReason.STOP, null, RateLimitInfo.EMPTY)
  override fun stream(request: ChatRequest): Flow<ChatDelta> = flow {
    streamed += request
    val calls = queue.removeFirstOrNull()
    if (calls == null) {
      emit(ChatDelta.Text("ok"))
      emit(ChatDelta.Finish(FinishReason.STOP, null, RateLimitInfo.EMPTY))
    } else {
      calls.forEachIndexed { index, name ->
        emit(ChatDelta.ToolCallPart(index, "call_$index", name, null))
        emit(ChatDelta.ToolCallPart(index, null, null, buildJsonObject { put("x", JsonPrimitive("$index")) }.toString()))
      }
      emit(ChatDelta.Finish(FinishReason.TOOL_CALLS, null, RateLimitInfo.EMPTY))
    }
  }
  override suspend fun listModels(): ModelCatalogue = ModelCatalogue(emptyList(), emptyList())
  override suspend fun transcribe(audio: File, mime: String, options: TranscribeOptions): Transcript = Transcript("", null)
}

class AiToolExecutionIdentityTest {

  private fun ask(tools: List<AiTool<Unit>>, chat: IdChat, prepare: (suspend (ToolCall) -> ToolExecution)?) = runBlocking {
    val registry = ToolRegistry(tools, IdGroup.entries)
    val router = AiRouter(IdGroup.entries, null, "un test", defaultGroups = listOf(IdGroup.MAIN))
    val common = AskInput(
      question = "fai", mode = AskMode.TEXT, language = "it", settings = AiSettings(),
      providers = listOf(ReadyProvider(chat, "chat", "stt", "piccolo")), toolContext = Unit,
      systemPrompt = "sei un assistente", conversation = Conversation(1L, 0L), actionsEnabled = true,
      preselectedGroups = setOf(IdGroup.MAIN),
    )
    val input = if (prepare == null) common else AskInput(
      question = common.question, mode = common.mode, language = common.language, settings = common.settings,
      providers = common.providers, toolContext = common.toolContext, systemPrompt = common.systemPrompt,
      conversation = common.conversation, actionsEnabled = common.actionsEnabled, preselectedGroups = common.preselectedGroups,
      prepareToolExecution = prepare,
    )
    AiOrchestrator(registry, router, AiDiagnosticsLog()).ask(input, MutableStateFlow(AssistantState.Idle))
  }

  @Test
  fun `ogni chiamata riceve la sua identita', quella preparata dall'app, una volta sola`() {
    val a = IdentityTool("primo")
    val b = IdentityTool("secondo")
    val chat = IdChat(listOf(listOf("primo", "secondo"), listOf("primo")))
    val prepared = Collections.synchronizedList(mutableListOf<String>())
    val counter = AtomicInteger()
    ask(listOf(a, b), chat) { call -> ToolExecution("op-${counter.incrementAndGet()}-${call.name}").also { prepared += it.operationId } }

    assertEquals(3, prepared.size)
    assertEquals(3, prepared.toSet().size)
    assertEquals(prepared.toSet(), (a.seen + b.seen).toSet())
    assertEquals(2, a.seen.size)
    assertEquals(0, a.legacyCalls + b.legacyCalls)
  }

  @Test
  fun `senza hook le identita' sono comunque diverse fra chiamate`() {
    val a = IdentityTool("primo")
    val chat = IdChat(listOf(listOf("primo", "primo"), listOf("primo")))
    ask(listOf(a), chat, prepare = null)
    assertEquals(3, a.seen.size)
    assertEquals(3, a.seen.toSet().size)
    assertTrue(a.seen.all { it.isNotBlank() })
  }

  @Test
  fun `un tool che implementa solo run(args, ctx) continua a funzionare`() {
    val legacy = LegacyTool()
    val chat = IdChat(listOf(listOf("vecchio")))
    ask(listOf(legacy), chat) { ToolExecution("op-ignorata") }
    assertEquals(1, legacy.calls)
    val result = chat.streamed[1].messages.filterIsInstance<Message.ToolResult>().single().content
    assertTrue(result, result.contains("vecchio ha risposto"))
  }

  @Test
  fun `se l'app non riesce a preparare l'identita' il tool non parte`() {
    val a = IdentityTool("primo")
    val chat = IdChat(listOf(listOf("primo")))
    ask(listOf(a), chat) { throw IllegalStateException("registro non scrivibile") }
    assertTrue(a.seen.isEmpty())
    val result = chat.streamed[1].messages.filterIsInstance<Message.ToolResult>().single().content
    assertTrue(result, result.contains("registro non scrivibile"))
  }
}
