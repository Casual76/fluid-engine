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
import dev.antigravity.fluidengine.ai.provider.TranscribeOptions
import dev.antigravity.fluidengine.ai.provider.Transcript
import dev.antigravity.fluidengine.ai.provider.Usage
import dev.antigravity.fluidengine.ai.tools.AiTool
import dev.antigravity.fluidengine.ai.tools.AiToolCategory
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
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Le categorie di un assistente finto a piu' app: l'engine non sa cosa siano, il test si'. */
private enum class Cat(override val id: String, override val label: String, override val hint: String) : AiToolCategory {
  METEO("meteo", "Meteo", "previsioni, pioggia"),
  BUS("bus", "Bus", "autobus, fermate"),
}

private enum class HG(
  override val id: String,
  override val statusKey: String,
  override val hint: String,
  override val category: AiToolCategory,
  override val loadsWithCategory: Boolean = false,
) : AiToolGroup {
  METEO_ADESSO("meteo_adesso", "hourly", "adesso e prossime ore", Cat.METEO, loadsWithCategory = true),
  METEO_RADAR("meteo_radar", "radar", "radar e pioggia in arrivo", Cat.METEO),
  BUS_ORARI("bus_orari", "schedule", "passaggi alle fermate", Cat.BUS),
  BUS_LIVE("bus_live", "live", "dove sono i bus", Cat.BUS),
}

private class HierarchyCtx

private sealed interface HierarchyScript {
  data class Text(val text: String) : HierarchyScript
  data class Calls(val calls: List<Pair<String, JsonObject>>) : HierarchyScript
  data class Fail(val error: Throwable) : HierarchyScript
}

private class HierarchyFakeProvider(
  override val id: ProviderId,
  turns: List<HierarchyScript>,
  private val classifier: String = """{"categoria":"nessuna","sottocategorie":[],"profondo":false}""",
) : ChatProvider {
  private val queue = ArrayDeque(turns)
  val requests = mutableListOf<ChatRequest>()
  val streamed = mutableListOf<ChatRequest>()

  override suspend fun complete(request: ChatRequest): ChatTurn {
    requests += request
    return ChatTurn(Message.Assistant(classifier), FinishReason.STOP, Usage(30, 10, 40), RateLimitInfo.EMPTY)
  }

  override fun stream(request: ChatRequest): Flow<ChatDelta> = flow {
    streamed += request
    when (val turn = queue.removeFirstOrNull() ?: HierarchyScript.Text("(fine copione)")) {
      is HierarchyScript.Fail -> throw turn.error
      is HierarchyScript.Text -> {
        emit(ChatDelta.Text(turn.text))
        emit(ChatDelta.Finish(FinishReason.STOP, Usage(100, 20, 120), RateLimitInfo(remainingRequests = 9)))
      }
      is HierarchyScript.Calls -> {
        turn.calls.forEachIndexed { index, (name, args) ->
          emit(ChatDelta.ToolCallPart(index, "call_$index", name, args.toString()))
        }
        emit(ChatDelta.Finish(FinishReason.TOOL_CALLS, Usage(100, 20, 120), RateLimitInfo.EMPTY))
      }
    }
  }

  override suspend fun listModels(): ModelCatalogue = ModelCatalogue(emptyList(), emptyList())
  override suspend fun transcribe(audio: File, mime: String, options: TranscribeOptions): Transcript = Transcript("", null)
}

private class HierarchyEchoTool(override val name: String, override val group: AiToolGroup) : AiTool<HierarchyCtx> {
  override val description = "eco"
  override val parameters = Schema.obj(mapOf("x" to Schema.str("x")))
  override suspend fun run(args: JsonObject, ctx: HierarchyCtx): ToolOutput = ToolOutput("$name: ok")
}

class AiHierarchyTest {

  private val registry = ToolRegistry(
    listOf(HierarchyEchoTool("adesso", HG.METEO_ADESSO), HierarchyEchoTool("radar", HG.METEO_RADAR), HierarchyEchoTool("passaggi", HG.BUS_ORARI), HierarchyEchoTool("bus_live", HG.BUS_LIVE)),
    HG.entries,
  )
  private val router = AiRouter(HG.entries, null, "un assistente a piu' app", defaultGroups = listOf(HG.METEO_ADESSO), categories = Cat.entries)
  private val diagnostics = AiDiagnosticsLog()
  private val events = mutableListOf<AiUsageEvent>()

  private fun orchestrator(config: AiOrchestratorConfig = AiOrchestratorConfig(maxRounds = 6)) =
    AiOrchestrator(registry, router, diagnostics, config = config, usageSink = { events += it })

  private fun ready(provider: HierarchyFakeProvider, deepModel: String = "profondo-${provider.id.id}") =
    ReadyProvider(provider, "modello-${provider.id.id}", "stt", "piccolo", deepModel = deepModel)

  private fun input(
    question: String,
    vararg providers: HierarchyFakeProvider,
    conversation: Conversation = Conversation(1L, 0L),
    hint: Set<AiToolGroup> = emptySet(),
    attachments: List<ContentPart> = emptyList(),
    fallback: suspend (ContentPart) -> String? = { null },
    deepModel: String? = null,
  ) = AskInput(
    question = question,
    mode = AskMode.TEXT,
    language = "it",
    settings = AiSettings(),
    providers = providers.map { if (deepModel != null) ready(it, deepModel) else ready(it) },
    toolContext = HierarchyCtx(),
    systemPrompt = "sei un assistente",
    conversation = conversation,
    actionsEnabled = true,
    routerHint = hint,
    attachments = attachments,
    attachmentFallback = fallback,
  )

  private fun call(name: String, key: String, value: String) = name to buildJsonObject { put(key, JsonPrimitive(value)) }

  private fun List<ChatRequest>.toolNames(index: Int) = this[index].tools.map { it.name }

  @Test
  fun `lo stadio 1 apre categoria e sottocategorie, che restano aperte alla domanda dopo`() = runBlocking {
    val conversation = Conversation(1L, 0L)
    val first = HierarchyFakeProvider(ProviderId.GROQ, listOf(HierarchyScript.Text("Piove.")), classifier = """{"categoria":"meteo","sottocategorie":["meteo_radar"],"profondo":false}""")
    orchestrator().ask(input("piove?", first, conversation = conversation), MutableStateFlow(AssistantState.Idle))
    val tools = first.streamed.toolNames(0)
    assertTrue(tools.contains("radar"))
    assertFalse(tools.contains("adesso"))
    // Le altre categorie si aprono con apri_categoria; le sottocategorie del meteo con apri_sottocategoria.
    assertTrue(tools.contains(ToolRegistry.OPEN_CATEGORY))
    assertTrue(tools.contains(ToolRegistry.OPEN_GROUP))
    assertFalse(tools.contains(ToolRegistry.MORE_TOOLS))
    val openGroup = first.streamed[0].tools.first { it.name == ToolRegistry.OPEN_GROUP }
    assertTrue(openGroup.description.contains("meteo_adesso"))
    assertFalse(openGroup.description.contains("bus_orari"))
    assertEquals(setOf(HG.METEO_RADAR), conversation.loadedGroups)

    // La domanda dopo non ha bisogno di strumenti: il radar resta lo stesso, e il router sa che il meteo e' aperto.
    val second = HierarchyFakeProvider(ProviderId.GROQ, listOf(HierarchyScript.Text("Ciao!")))
    orchestrator().ask(input("ciao", second, conversation = conversation), MutableStateFlow(AssistantState.Idle))
    assertTrue(second.streamed.toolNames(0).contains("radar"))
    val routerPrompt = second.requests.single().messages.filterIsInstance<Message.System>().single().text
    assertTrue(routerPrompt.contains("gia' aperte in questa conversazione"))
    assertTrue(routerPrompt.contains("meteo"))
  }

  @Test
  fun `una categoria senza sottocategorie apre i gruppi di partenza, o tutti se l'app non ne ha marcati`() = runBlocking {
    val meteo = HierarchyFakeProvider(ProviderId.GROQ, listOf(HierarchyScript.Text("Sereno.")), classifier = """{"categoria":"meteo","sottocategorie":[],"profondo":false}""")
    orchestrator().ask(input("che tempo fa?", meteo), MutableStateFlow(AssistantState.Idle))
    val meteoTools = meteo.streamed.toolNames(0)
    assertTrue(meteoTools.contains("adesso"))
    assertFalse(meteoTools.contains("radar"))

    val bus = HierarchyFakeProvider(ProviderId.GROQ, listOf(HierarchyScript.Text("Fra 5 minuti.")), classifier = """{"categoria":"bus","sottocategorie":[],"profondo":false}""")
    orchestrator().ask(input("quando passa?", bus), MutableStateFlow(AssistantState.Idle))
    val busTools = bus.streamed.toolNames(0)
    assertTrue(busTools.containsAll(listOf("passaggi", "bus_live")))
    assertFalse(busTools.contains("adesso"))
  }

  @Test
  fun `senza categoria al modello arrivano solo i tool built-in`() = runBlocking {
    val groq = HierarchyFakeProvider(ProviderId.GROQ, listOf(HierarchyScript.Text("Ciao!")))
    orchestrator().ask(input("ciao", groq), MutableStateFlow(AssistantState.Idle))
    val tools = groq.streamed.toolNames(0)
    assertEquals(listOf(ToolRegistry.OPEN_CATEGORY, ToolRegistry.DEEP_MODEL), tools)
    assertTrue(groq.streamed[0].tools.first().description.contains("meteo = previsioni, pioggia (meteo_adesso, meteo_radar)"))
  }

  @Test
  fun `apri_categoria e apri_sottocategoria valgono dal giro dopo e restano nella conversazione`() = runBlocking {
    val conversation = Conversation(1L, 0L)
    val groq = HierarchyFakeProvider(
      ProviderId.GROQ,
      listOf(
        HierarchyScript.Calls(listOf(call(ToolRegistry.OPEN_CATEGORY, "categoria", "bus"))),
        HierarchyScript.Calls(listOf(call(ToolRegistry.OPEN_GROUP, "sottocategoria", "meteo_radar"))),
        HierarchyScript.Calls(listOf(call("radar", "x", "1"), call("passaggi", "x", "2"))),
        HierarchyScript.Text("Piove e il bus e' in ritardo."),
      ),
    )
    val result = orchestrator().ask(input("piove? e il bus?", groq, conversation = conversation), MutableStateFlow(AssistantState.Idle))
    assertEquals("Piove e il bus e' in ritardo.", result.answer)
    assertFalse(groq.streamed.toolNames(0).contains("passaggi"))
    assertTrue(groq.streamed.toolNames(1).containsAll(listOf("passaggi", "bus_live")))
    assertFalse(groq.streamed.toolNames(1).contains("radar"))
    assertTrue(groq.streamed.toolNames(2).contains("radar"))
    // Il testo di ritorno dice la verita' su cosa arriva.
    val results = groq.streamed[1].messages.filterIsInstance<Message.ToolResult>()
    val opened = results.first { it.name == ToolRegistry.OPEN_CATEGORY }
    assertTrue(opened.content.startsWith("ok: gli strumenti della categoria bus"))
    assertTrue(opened.content.contains("bus_orari, bus_live"))
    assertTrue(results.first { it.name == ToolRegistry.OPEN_GROUP }.content.startsWith("ok: gli strumenti di meteo_radar"))
    assertEquals(setOf(HG.BUS_ORARI, HG.BUS_LIVE, HG.METEO_RADAR), conversation.loadedGroups)
    assertEquals(setOf(Cat.BUS, Cat.METEO), conversation.loadedCategories)
    assertEquals(listOf("radar", "passaggi"), result.toolsUsed.filter { it in listOf("radar", "passaggi") })
  }

  @Test
  fun `oltre il tetto cadono i gruppi usati meno di recente, mai quello appena aperto`() = runBlocking {
    val conversation = Conversation(1L, 0L)
    conversation.touch(listOf(HG.METEO_ADESSO, HG.METEO_RADAR))
    conversation.loadedCategories += Cat.METEO
    val groq = HierarchyFakeProvider(ProviderId.GROQ, listOf(HierarchyScript.Text("Ok.")), classifier = """{"categoria":"bus","sottocategorie":["bus_orari"],"profondo":false}""")
    orchestrator(AiOrchestratorConfig(maxRounds = 6, maxLoadedTools = 2)).ask(input("?", groq, conversation = conversation), MutableStateFlow(AssistantState.Idle))
    val tools = groq.streamed.toolNames(0)
    assertTrue(tools.containsAll(listOf("passaggi", "radar")))
    assertFalse(tools.contains("adesso"))
    assertEquals(setOf(HG.METEO_RADAR, HG.BUS_ORARI), conversation.loadedGroups)
    // La categoria resta aperta: il gruppo caduto si riapre con apri_sottocategoria.
    val openGroup = groq.streamed[0].tools.first { it.name == ToolRegistry.OPEN_GROUP }
    assertTrue(openGroup.description.contains("meteo_adesso"))
  }

  @Test
  fun `su OpenRouter col catalogo gerarchico non parte tutto, solo i gruppi aperti e il suggerimento`() = runBlocking {
    val openRouter = HierarchyFakeProvider(ProviderId.OPENROUTER, listOf(HierarchyScript.Text("Fra 3 minuti.")))
    orchestrator().ask(input("quando passa il 23?", openRouter, hint = setOf(HG.BUS_ORARI)), MutableStateFlow(AssistantState.Idle))
    assertTrue(openRouter.requests.isEmpty())
    val tools = openRouter.streamed.toolNames(0)
    assertTrue(tools.contains("passaggi"))
    assertFalse(tools.contains("radar"))
    assertTrue(tools.contains(ToolRegistry.OPEN_CATEGORY))
  }

  @Test
  fun `un allegato dell'utente che il modello della chat regge entra nel suo messaggio`() = runBlocking {
    val gemini = HierarchyFakeProvider(ProviderId.GEMINI, listOf(HierarchyScript.Text("E' uno screenshot di una chat.")))
    val image = ContentPart.Image(byteArrayOf(1, 2, 3), "image/jpeg")
    val conversation = Conversation(1L, 0L)
    val result = orchestrator().ask(input("cosa c'e' qui?", gemini, conversation = conversation, attachments = listOf(image)), MutableStateFlow(AssistantState.Idle))
    val user = gemini.streamed.single().messages.filterIsInstance<Message.User>().last()
    assertTrue(user.hasBinaryParts)
    assertEquals("cosa c'e' qui?", user.text)
    assertEquals("modello-gemini", gemini.streamed.single().model)
    assertEquals(ModelTier.CHAT, result.tierReached)
    // Lo scambio ricorda l'allegato: la domanda dopo lo ritrova nella storia.
    assertEquals(listOf(image), conversation.exchanges.single().attachments)
    val next = HierarchyFakeProvider(ProviderId.GEMINI, listOf(HierarchyScript.Text("A destra c'e' un tasto.")))
    orchestrator().ask(input("e a destra?", next, conversation = conversation), MutableStateFlow(AssistantState.Idle))
    val history = next.streamed.single().messages.filterIsInstance<Message.User>()
    assertTrue(history.first().hasBinaryParts)
  }

  @Test
  fun `un allegato che regge solo il livello profondo fa partire da li'`() = runBlocking {
    val groq = HierarchyFakeProvider(ProviderId.GROQ, listOf(HierarchyScript.Text("Vedo un grafico.")))
    val image = ContentPart.Image(byteArrayOf(1), "image/png")
    val result = orchestrator().ask(input("cos'e'?", groq, attachments = listOf(image), deepModel = "llama-4-scout"), MutableStateFlow(AssistantState.Idle))
    assertEquals("llama-4-scout", groq.streamed.single().model)
    assertEquals(ModelTier.DEEP, result.tierReached)
    assertTrue(groq.streamed.single().messages.filterIsInstance<Message.User>().last().hasBinaryParts)
  }

  @Test
  fun `un allegato che nessun modello regge passa dalla traduzione dell'app, o viene detto al modello`() = runBlocking {
    val pdf = ContentPart.Document(byteArrayOf(1), "application/pdf", "circolare.pdf")
    val translated = HierarchyFakeProvider(ProviderId.GROQ, listOf(HierarchyScript.Text("La gita e' il 12.")))
    orchestrator().ask(input("cosa dice?", translated, attachments = listOf(pdf), fallback = { "TESTO: gita il 12" }), MutableStateFlow(AssistantState.Idle))
    val user = translated.streamed.single().messages.filterIsInstance<Message.User>().last()
    assertFalse(user.hasBinaryParts)
    assertTrue(user.text.contains("cosa dice?"))
    assertTrue(user.text.contains("TESTO: gita il 12"))
    assertTrue(user.text.contains("circolare.pdf"))
    assertEquals("modello-groq", translated.streamed.single().model)

    val unreadable = HierarchyFakeProvider(ProviderId.GROQ, listOf(HierarchyScript.Text("Non lo posso leggere.")))
    orchestrator().ask(input("cosa dice?", unreadable, attachments = listOf(pdf)), MutableStateFlow(AssistantState.Idle))
    assertTrue(unreadable.streamed.single().messages.filterIsInstance<Message.User>().last().text.contains("non e' leggibile"))
  }

  @Test
  fun `l'allegato nella storia diventa una riga quando il nuovo provider non lo regge`() = runBlocking {
    val conversation = Conversation(1L, 0L)
    conversation.exchanges += Exchange("cosa c'e'?", "Una foto.", emptyList(), ProviderId.GEMINI, 0L, attachments = listOf(ContentPart.Image(byteArrayOf(1), "image/png")))
    val groq = HierarchyFakeProvider(ProviderId.GROQ, listOf(HierarchyScript.Text("Non la vedo piu'.")))
    orchestrator().ask(input("e a destra?", groq, conversation = conversation), MutableStateFlow(AssistantState.Idle))
    val history = groq.streamed.single().messages.filterIsInstance<Message.User>().first()
    assertFalse(history.hasBinaryParts)
    assertTrue(history.text.contains("cosa c'e'?"))
    assertTrue(history.text.contains("non puo' leggere"))
  }

  @Test
  fun `il sink dei consumi riceve un evento per ogni chiamata, router compreso, anche quando fallisce`() = runBlocking {
    val groq = HierarchyFakeProvider(
      ProviderId.GROQ,
      listOf(HierarchyScript.Fail(AiError.RateLimited(1.0, RateLimitInfo(remainingRequests = 0), message = "429")), HierarchyScript.Text("Ora va.")),
      classifier = """{"categoria":"meteo","sottocategorie":["meteo_adesso"],"profondo":false}""",
    )
    val result = orchestrator().ask(input("che tempo fa?", groq), MutableStateFlow(AssistantState.Idle))
    assertEquals("Ora va.", result.answer)
    assertEquals(3, events.size)
    val routerEvent = events[0]
    assertEquals(ModelTier.ROUTER, routerEvent.tier)
    assertEquals("piccolo", routerEvent.model)
    assertEquals(40, routerEvent.usage?.totalTokens)
    val failed = events[1]
    assertTrue(failed.rateLimited)
    assertEquals(0, failed.rateLimit.remainingRequests)
    val ok = events[2]
    assertEquals(ModelTier.CHAT, ok.tier)
    assertEquals(120, ok.usage?.totalTokens)
    assertEquals(9, ok.rateLimit.remainingRequests)
    assertTrue(events.all { it.conversationId == 1L && it.provider == ProviderId.GROQ })
  }
}
