package dev.antigravity.fluidengine.ai.bridge

import dev.antigravity.fluidengine.ai.keys.AiSettings
import dev.antigravity.fluidengine.ai.net.RateLimitInfo
import dev.antigravity.fluidengine.ai.orchestrator.AiDiagnosticsLog
import dev.antigravity.fluidengine.ai.orchestrator.AiOrchestrator
import dev.antigravity.fluidengine.ai.orchestrator.AiRouter
import dev.antigravity.fluidengine.ai.orchestrator.AskInput
import dev.antigravity.fluidengine.ai.orchestrator.AskMode
import dev.antigravity.fluidengine.ai.orchestrator.AssistantState
import dev.antigravity.fluidengine.ai.orchestrator.Conversation
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
import dev.antigravity.fluidengine.ai.tools.ConfirmationText
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
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/*
 * Un tool remoto che scrive, eseguito come lo esegue davvero un assistente: dentro l'orchestratore.
 * La 3.0.6 candidata lo bloccava («operation identity required for writes») perche'
 * l'orchestratore chiamava ancora `run(args, ctx)`, che per una scrittura remota non puo' inventare
 * un'identita'. Questi test fissano il contratto: un'identita' per chiamata di tool, la stessa
 * durante la conferma, diversa fra chiamate diverse, e quella che l'app sceglie quando ripete.
 */

/** Una chiamata arrivata all'ospite finto. */
private data class HostRun(val name: String, val confirmed: Boolean, val requestId: String)

/** L'ospite finto: annota ogni `run` (thread-safe: i tool del giro girano in parallelo). */
private class OrchestratedHost(private val answer: (Int) -> RemoteResult = { RemoteResult.Done("fatto", false, emptyList()) }) : BridgeCalls {
  val runs: MutableList<HostRun> = Collections.synchronizedList(mutableListOf())
  private val count = AtomicInteger()

  override suspend fun describe(authority: String, name: String, args: JsonObject, language: String): Pair<String?, String?>? = "Eseguire $name?" to null

  override suspend fun run(authority: String, name: String, args: JsonObject, confirmed: Boolean, requestId: String, language: String): RemoteResult {
    runs += HostRun(name, confirmed, requestId)
    return answer(count.getAndIncrement())
  }

  override suspend fun status(authority: String, jobId: String): RemoteResult = RemoteResult.Error("nessun lavoro lungo qui")
  override suspend fun cancel(authority: String, jobId: String) = Unit
  override fun readPart(part: RemotePart): ByteArray? = null
}

/** Il cancello dell'assistente: dice si' o no, e annota quante volte ha chiesto. */
private class OrchestratedConfirm(private val refuse: Boolean = false) : RemoteToolHost<Unit> {
  val asked: MutableList<String> = Collections.synchronizedList(mutableListOf())
  override suspend fun confirm(ctx: Unit, toolName: String, text: ConfirmationText): ToolOutput? {
    asked += toolName
    return if (refuse) ToolOutput("l'utente ha detto di no: non fatto") else null
  }
}

/** Un provider recitato: ogni giro e' un elenco di chiamate (nome, argomenti) o, finito il copione, una risposta. */
private class ScriptedChat(turns: List<List<Pair<String, JsonObject>>>) : ChatProvider {
  override val id: ProviderId = ProviderId.GROQ
  private val queue = ArrayDeque(turns)
  val streamed = mutableListOf<ChatRequest>()

  override suspend fun complete(request: ChatRequest): ChatTurn =
    ChatTurn(Message.Assistant("""{"gruppi":["g"],"profondo":false}"""), FinishReason.STOP, null, RateLimitInfo.EMPTY)

  override fun stream(request: ChatRequest): Flow<ChatDelta> = flow {
    streamed += request
    val calls = queue.removeFirstOrNull()
    if (calls == null) {
      emit(ChatDelta.Text("ok"))
      emit(ChatDelta.Finish(FinishReason.STOP, null, RateLimitInfo.EMPTY))
    } else {
      // Gli id ripartono da call_0 a ogni giro, come fanno davvero alcuni provider: non sono
      // un'identita' dell'operazione.
      calls.forEachIndexed { index, (name, args) ->
        emit(ChatDelta.ToolCallPart(index, "call_$index", name, null))
        emit(ChatDelta.ToolCallPart(index, null, null, args.toString()))
      }
      emit(ChatDelta.Finish(FinishReason.TOOL_CALLS, null, RateLimitInfo.EMPTY))
    }
  }

  override suspend fun listModels(): ModelCatalogue = ModelCatalogue(emptyList(), emptyList())
  override suspend fun transcribe(audio: File, mime: String, options: TranscribeOptions): Transcript = Transcript("", null)
}

private val orchestratedCatalog = RemoteCatalog(
  protocol = 1, authority = "fluidify.ai.tools", packageName = "fluidify", appId = "fluidify", domain = "fy", appLabel = "Fluidify",
  appVersion = "2.1.0", hint = null, vocabulary = emptyList(), categories = emptyList(),
  groups = listOf(RemoteGroupInfo("g", "g", "", null, null, loadsWithCategory = true, action = false)),
  tools = emptyList(),
)

private val orchestratedGroup = RemoteGroup("g", "g", "il gruppo remoto", null, false, remoteId = "g")

private val trackSchema = Schema.obj(mapOf("track" to Schema.str("il brano")))

private fun write(name: String) =
  RemoteToolInfo(name, "g", "scrive", trackSchema, needsConfirmation = true, longRunning = false, action = true)

private fun track(id: String) = buildJsonObject { put("track", JsonPrimitive(id)) }

class RemoteToolOrchestrationTest {

  private fun orchestrator(tools: List<RemoteTool<Unit>>): AiOrchestrator<Unit> {
    val registry = ToolRegistry(tools, listOf(orchestratedGroup))
    val router = AiRouter(listOf(orchestratedGroup), null, "un test", defaultGroups = listOf(orchestratedGroup))
    return AiOrchestrator(registry, router, AiDiagnosticsLog())
  }

  private fun input(
    chat: ScriptedChat,
    conversation: Conversation = Conversation(1L, 0L),
    prepare: (suspend (ToolCall) -> ToolExecution)? = null,
  ): AskInput<Unit> {
    val base = AskInput(
      question = "aggiungi il brano",
      mode = AskMode.TEXT,
      language = "it",
      settings = AiSettings(),
      providers = listOf(ReadyProvider(chat, "chat", "stt", "piccolo")),
      toolContext = Unit,
      systemPrompt = "sei un assistente",
      conversation = conversation,
      actionsEnabled = true,
      preselectedGroups = setOf(orchestratedGroup),
    )
    if (prepare == null) return base
    return AskInput(
      question = base.question, mode = base.mode, language = base.language, settings = base.settings,
      providers = base.providers, toolContext = base.toolContext, systemPrompt = base.systemPrompt,
      conversation = base.conversation, actionsEnabled = base.actionsEnabled, preselectedGroups = base.preselectedGroups,
      prepareToolExecution = prepare,
    )
  }

  private fun toolResults(chat: ScriptedChat, round: Int): List<Message.ToolResult> =
    chat.streamed[round].messages.filterIsInstance<Message.ToolResult>()

  @Test
  fun `una scrittura remota con conferma arriva all'ospite una volta, con l'identita' preparata`() = runBlocking {
    val host = OrchestratedHost()
    val confirm = OrchestratedConfirm()
    val tool = RemoteTool(host, orchestratedCatalog, write("playlist_add"), orchestratedGroup, confirm)
    val chat = ScriptedChat(listOf(listOf(tool.name to track("t1"))))
    val prepared = Collections.synchronizedList(mutableListOf<String>())

    orchestrator(listOf(tool)).ask(
      input(chat, prepare = { call -> ToolExecution("op-${call.name}-1").also { prepared += it.operationId } }),
      MutableStateFlow(AssistantState.Idle),
    )

    // Prima della correzione: zero chiamate all'ospite e «operation identity required for writes».
    assertEquals(listOf(HostRun("playlist_add", confirmed = true, requestId = "op-fy_playlist_add-1")), host.runs)
    assertEquals(listOf(tool.name), confirm.asked)
    assertEquals(listOf("op-fy_playlist_add-1"), prepared)
    val result = toolResults(chat, 1).single().content
    assertFalse(result, result.contains("operation identity required"))
    assertTrue(result, result.contains("fatto"))
  }

  @Test
  fun `senza un hook dell'app l'orchestratore da' comunque un'identita' alla scrittura remota`() = runBlocking {
    val host = OrchestratedHost()
    val tool = RemoteTool(host, orchestratedCatalog, write("playlist_add"), orchestratedGroup, OrchestratedConfirm())
    val chat = ScriptedChat(listOf(listOf(tool.name to track("t1"))))

    orchestrator(listOf(tool)).ask(input(chat), MutableStateFlow(AssistantState.Idle))

    assertEquals(1, host.runs.size)
    assertTrue(host.runs.single().confirmed)
    assertTrue(host.runs.single().requestId.isNotBlank())
  }

  @Test
  fun `una conferma rifiutata non raggiunge l'ospite`() = runBlocking {
    val host = OrchestratedHost()
    val confirm = OrchestratedConfirm(refuse = true)
    val tool = RemoteTool(host, orchestratedCatalog, write("playlist_add"), orchestratedGroup, confirm)
    val chat = ScriptedChat(listOf(listOf(tool.name to track("t1"))))
    val prepared = AtomicInteger()

    orchestrator(listOf(tool)).ask(
      input(chat, prepare = { prepared.incrementAndGet(); ToolExecution("op-rifiutata") }),
      MutableStateFlow(AssistantState.Idle),
    )

    assertTrue(host.runs.isEmpty())
    assertEquals(listOf(tool.name), confirm.asked)
    assertEquals(1, prepared.get())
    assertTrue(toolResults(chat, 1).single().content.contains("l'utente ha detto di no"))
  }

  @Test
  fun `dopo un errore di rete la ripetizione usa l'identita' che l'app ha conservato`() = runBlocking {
    // Primo tentativo: l'ospite muore a meta' (l'effetto e' incerto). Il modello ripete la stessa
    // scrittura al giro dopo, con un id di chiamata che il provider ha rimesso a call_0.
    val host = OrchestratedHost { attempt ->
      if (attempt == 0) RemoteResult.Error("l'app non risponde: aprila e riprova (DeadObjectException)")
      else RemoteResult.Done("aggiunto", false, emptyList())
    }
    val tool = RemoteTool(host, orchestratedCatalog, write("playlist_add"), orchestratedGroup, OrchestratedConfirm())
    val chat = ScriptedChat(listOf(listOf(tool.name to track("t1")), listOf(tool.name to track("t1"))))
    // Il registro delle operazioni dell'app (in PampAI e' persistente): stessa scrittura, stessa identita'.
    val ledger = mutableMapOf<String, ToolExecution>()
    var minted = 0

    orchestrator(listOf(tool)).ask(
      input(chat, prepare = { call ->
        synchronized(ledger) { ledger.getOrPut("${call.name}|${call.arguments}") { ToolExecution("ledger-${++minted}") } }
      }),
      MutableStateFlow(AssistantState.Idle),
    )

    assertEquals(2, host.runs.size)
    assertEquals(listOf("ledger-1", "ledger-1"), host.runs.map { it.requestId })
    // Il primo risultato (l'ospite morto a meta') arriva al modello come incerto, non come fallito.
    val first = chat.streamed.last().messages.filterIsInstance<Message.ToolResult>().first().content
    assertTrue(first, first.contains("outcome=UNCERTAIN"))
  }

  @Test
  fun `chiamate diverse dello stesso giro hanno identita' diverse, anche con lo stesso id di chiamata fra i giri`() = runBlocking {
    val host = OrchestratedHost()
    val confirm = OrchestratedConfirm()
    val add = RemoteTool(host, orchestratedCatalog, write("playlist_add"), orchestratedGroup, confirm)
    val like = RemoteTool(host, orchestratedCatalog, write("track_like"), orchestratedGroup, confirm)
    val chat = ScriptedChat(listOf(listOf(add.name to track("t1"), like.name to track("t2")), listOf(add.name to track("t3"))))

    orchestrator(listOf(add, like)).ask(input(chat), MutableStateFlow(AssistantState.Idle))

    assertEquals(3, host.runs.size)
    assertTrue(host.runs.all { it.confirmed })
    val ids = host.runs.map { it.requestId }
    assertEquals(3, ids.toSet().size)
    assertEquals(setOf("playlist_add", "track_like"), host.runs.take(2).map { it.name }.toSet())
    assertNotEquals(host.runs[0].requestId, host.runs[2].requestId)
    assertEquals(3, confirm.asked.size)
  }

  @Test
  fun `chiamata direttamente senza identita' una scrittura remota resta rifiutata`() = runBlocking {
    val host = OrchestratedHost()
    val tool = RemoteTool(host, orchestratedCatalog, write("playlist_add"), orchestratedGroup, OrchestratedConfirm())
    assertTrue(tool.run(track("t1"), Unit).text.contains("operation identity required for writes"))
    assertTrue(host.runs.isEmpty())
  }
}
