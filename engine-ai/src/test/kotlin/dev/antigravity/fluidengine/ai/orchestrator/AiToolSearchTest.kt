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
import dev.antigravity.fluidengine.ai.provider.Usage
import dev.antigravity.fluidengine.ai.tools.AiTool
import dev.antigravity.fluidengine.ai.tools.AiToolCategory
import dev.antigravity.fluidengine.ai.tools.AiToolGroup
import dev.antigravity.fluidengine.ai.tools.Schema
import dev.antigravity.fluidengine.ai.tools.ToolOutput
import dev.antigravity.fluidengine.ai.tools.ToolRegistry
import dev.antigravity.fluidengine.ai.tools.ToolSearch
import java.io.File
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

private enum class SCat(override val id: String, override val label: String, override val hint: String) : AiToolCategory {
  NOTE("note", "Note", "le note dell'utente"),
  METEO("meteo", "Fluid Weather", "il meteo: adesso, le previsioni, la pioggia, il radar, la luna"),
}

private enum class SGroup(
  override val id: String,
  override val statusKey: String,
  override val hint: String,
  override val category: AiToolCategory,
  override val loadsWithCategory: Boolean = false,
) : AiToolGroup {
  NOTE("note", "notes", "scrivere e cercare note", SCat.NOTE, loadsWithCategory = true),
  METEO_ADESSO("meteo_orario", "hourly", "il tempo di adesso e le prossime ore", SCat.METEO, loadsWithCategory = true),
  METEO_CIELO("meteo_cielo", "sky", "sole e luna", SCat.METEO),
  METEO_APP("meteo_app", "app", "azioni nell'app del meteo", SCat.METEO),
}

private class STool(override val name: String, override val group: AiToolGroup, override val description: String, override val isAction: Boolean = false) : AiTool<Unit> {
  override val parameters = Schema.obj(mapOf("luogo" to Schema.str("il posto")))
  override suspend fun run(args: JsonObject, ctx: Unit): ToolOutput = ToolOutput("$name: ok\n" + "riga di dati\n".repeat(400))
}

private sealed interface SScript {
  data class Text(val text: String) : SScript
  data class Call(val name: String, val args: Map<String, String> = emptyMap()) : SScript
}

private class SProvider(turns: List<SScript>) : ChatProvider {
  override val id: ProviderId = ProviderId.GROQ
  private val queue = ArrayDeque(turns)
  val streamed = mutableListOf<ChatRequest>()
  val classified = mutableListOf<ChatRequest>()

  override suspend fun complete(request: ChatRequest): ChatTurn {
    classified += request
    return ChatTurn(Message.Assistant("""{"categoria":"note","sottocategorie":["note"],"profondo":false}"""), FinishReason.STOP, Usage(1, 1, 2), RateLimitInfo.EMPTY)
  }

  override fun stream(request: ChatRequest): Flow<ChatDelta> = flow {
    streamed += request
    when (val turn = queue.removeFirstOrNull() ?: SScript.Text("fine")) {
      is SScript.Text -> {
        emit(ChatDelta.Text(turn.text))
        emit(ChatDelta.Finish(FinishReason.STOP, Usage(1, 1, 2), RateLimitInfo.EMPTY))
      }
      is SScript.Call -> {
        val args = JsonObject(turn.args.mapValues { JsonPrimitive(it.value) })
        emit(ChatDelta.ToolCallPart(0, "call_${streamed.size}", turn.name, args.toString()))
        emit(ChatDelta.Finish(FinishReason.TOOL_CALLS, Usage(1, 1, 2), RateLimitInfo.EMPTY))
      }
    }
  }

  override suspend fun listModels(): ModelCatalogue = ModelCatalogue(emptyList(), emptyList())
  override suspend fun transcribe(audio: File, mime: String, options: TranscribeOptions): Transcript = Transcript("", null)
}

class AiToolSearchTest {

  private val tools = listOf(
    STool("nota_crea", SGroup.NOTE, "Scrive una nota nuova"),
    STool("nota_cerca", SGroup.NOTE, "Cerca fra le note"),
    STool("meteo_adesso", SGroup.METEO_ADESSO, "Le condizioni di adesso: temperatura, vento, umidita'"),
    STool("meteo_radar", SGroup.METEO_ADESSO, "Il radar della pioggia attorno al posto"),
    STool("meteo_luna", SGroup.METEO_CIELO, "Fase lunare, sorgere e tramontare della luna"),
    STool("meteo_sole", SGroup.METEO_CIELO, "Alba e tramonto del sole"),
    STool("meteo_salva_luogo", SGroup.METEO_APP, "Salva una localita' del meteo", isAction = true),
  )
  private val deferred = setOf(SGroup.METEO_ADESSO, SGroup.METEO_CIELO, SGroup.METEO_APP)
  private val registry = ToolRegistry(tools, SGroup.entries, deferred = deferred, searchTerms = mapOf(SGroup.METEO_ADESSO to listOf("piove", "ombrello")))

  private fun router(r: ToolRegistry<Unit> = registry) = AiRouter(r.homeGroups, null, "un assistente", defaultGroups = listOf(SGroup.NOTE), categories = r.categories)

  private fun input(question: String, provider: SProvider, conversation: Conversation = Conversation(1L, 0L), actions: Boolean = true, skipRouter: Boolean = false) = AskInput(
    question = question,
    mode = AskMode.TEXT,
    language = "it",
    settings = AiSettings(),
    providers = listOf(ReadyProvider(provider, "chat", "stt", "router", deepModel = "profondo")),
    toolContext = Unit,
    systemPrompt = "sei un assistente",
    conversation = conversation,
    actionsEnabled = actions,
    skipRouter = skipRouter,
  )

  private fun ask(provider: SProvider, question: String, config: AiOrchestratorConfig = AiOrchestratorConfig(), conversation: Conversation = Conversation(1L, 0L), actions: Boolean = true, skipRouter: Boolean = false) =
    runBlocking { AiOrchestrator(registry, router(), AiDiagnosticsLog(), config = config).ask(input(question, provider, conversation, actions, skipRouter), MutableStateFlow(AssistantState.Idle)) }

  private fun ChatRequest.names() = tools.map { it.name }

  @Test
  fun `i gruppi differiti non stanno fra le categorie, nel router ne' in apri_categoria`() {
    assertEquals(listOf(SCat.NOTE), registry.categories)
    assertEquals(listOf(SCat.METEO), registry.deferredCategories)
    assertEquals(listOf(SGroup.NOTE), registry.homeGroups)
    assertTrue(registry.searchable)
    assertFalse(registry.openCategoryTool.description.contains("meteo"))
    assertFalse(router().prompt("it", true).contains("meteo_orario"))
    // La ricerca nomina le app raggiungibili, senza le loro righe.
    assertTrue(registry.searchTool.description.contains("Fluid Weather"))
    assertFalse(registry.searchTool.description.contains("luna"))
    // In un catalogo piatto i differiti non sono fra i gruppi di altri_tool.
    val flat = ToolRegistry(listOf(STool("a", FlatGroup.A, "a"), STool("b", FlatGroup.B, "b")), FlatGroup.entries, deferred = setOf(FlatGroup.B))
    val enum = flat.moreTools.parameters["properties"]!!.jsonObject["gruppo"]!!.jsonObject["enum"]!!.jsonArray.map { it.jsonPrimitive.content }
    assertEquals(listOf("a"), enum)
    assertEquals(listOf(FlatGroup.A), flat.visibleGroups(true))
  }

  @Test
  fun `cerca_strumenti carica i tool trovati al giro dopo, uno per uno, senza aprire il loro gruppo`() {
    val provider = SProvider(listOf(SScript.Call(ToolRegistry.SEARCH_TOOLS, mapOf("cosa" to "radar della pioggia")), SScript.Call("meteo_radar"), SScript.Text("Piove a nord.")))
    val conversation = Conversation(1L, 0L)
    val result = ask(provider, "fammi un riassunto", conversation = conversation)
    assertEquals("Piove a nord.", result.answer)
    val first = provider.streamed[0].names()
    assertTrue(ToolRegistry.SEARCH_TOOLS in first)
    assertTrue(first.none { it.startsWith("meteo_") })
    val second = provider.streamed[1].names()
    assertTrue("meteo_radar" in second)
    assertFalse("meteo_luna" in second)
    // Il testo del risultato dice cosa e' arrivato e da dove.
    val found = provider.streamed[1].messages.filterIsInstance<Message.ToolResult>().single { it.name == ToolRegistry.SEARCH_TOOLS }.content
    assertTrue(found, found.contains("meteo_radar (Fluid Weather)"))
    // Il tool usato resta caricato da solo: il suo gruppo (con meteo_adesso) non si apre.
    assertTrue("meteo_radar" in conversation.loadedTools)
    assertFalse(SGroup.METEO_ADESSO in conversation.loadedGroups)
    assertFalse("meteo_adesso" in provider.streamed[2].names())
    // E alla domanda dopo c'e' ancora.
    val next = SProvider(listOf(SScript.Text("ok")))
    ask(next, "e domani?", conversation = conversation)
    assertTrue("meteo_radar" in next.streamed[0].names())
  }

  @Test
  fun `una ricerca senza risultati lo dice, e con le azioni spente non porta i tool d'azione`() {
    val provider = SProvider(listOf(SScript.Call(ToolRegistry.SEARCH_TOOLS, mapOf("cosa" to "prenota un volo")), SScript.Text("Non posso.")))
    ask(provider, "prenotami un volo")
    val text = provider.streamed[1].messages.filterIsInstance<Message.ToolResult>().single().content
    assertTrue(text, text.contains("nessuno strumento trovato"))

    val off = SProvider(listOf(SScript.Call(ToolRegistry.SEARCH_TOOLS, mapOf("cosa" to "salva localita' meteo")), SScript.Text("ok")))
    val registryWithAction = ToolRegistry(tools, SGroup.entries, actionGroup = SGroup.METEO_APP, deferred = deferred)
    runBlocking {
      AiOrchestrator(registryWithAction, router(registryWithAction), AiDiagnosticsLog()).ask(input("salva Bologna", off, actions = false), MutableStateFlow(AssistantState.Idle))
    }
    assertFalse("meteo_salva_luogo" in off.streamed[1].names())
  }

  @Test
  fun `il precaricamento porta subito i tool che la domanda nomina, e una domanda generica niente`() {
    val provider = SProvider(listOf(SScript.Call("meteo_luna"), SScript.Text("Luna piena.")))
    ask(provider, "che fase ha la luna stasera?", config = AiOrchestratorConfig(searchPreloadMinScore = 1.0))
    val first = provider.streamed[0].names()
    assertTrue(first.toString(), "meteo_luna" in first)
    // Con la soglia di serie una domanda che non c'entra non carica niente.
    val generic = SProvider(listOf(SScript.Text("La fotosintesi e'...")))
    ask(generic, "spiegami la fotosintesi clorofilliana")
    assertTrue(generic.streamed[0].names().none { it.startsWith("meteo_") })
    // Spento: niente.
    val off = SProvider(listOf(SScript.Text("ok")))
    ask(off, "che fase ha la luna stasera?", config = AiOrchestratorConfig(searchPreload = 0, searchPreloadMinScore = 1.0))
    assertTrue(off.streamed[0].names().none { it.startsWith("meteo_") })
  }

  @Test
  fun `senza router non parte la chiamata dello stadio 1`() {
    val provider = SProvider(listOf(SScript.Text("ok")))
    ask(provider, "scrivi una nota", skipRouter = true)
    assertTrue(provider.classified.isEmpty())
    val withRouter = SProvider(listOf(SScript.Text("ok")))
    ask(withRouter, "scrivi una nota")
    assertEquals(1, withRouter.classified.size)
  }

  @Test
  fun `i tool cercati hanno un tetto per conversazione, e cadono i piu' vecchi`() {
    val conversation = Conversation(1L, 0L)
    val provider = SProvider(
      listOf(
        SScript.Call(ToolRegistry.SEARCH_TOOLS, mapOf("cosa" to "luna")),
        SScript.Call(ToolRegistry.SEARCH_TOOLS, mapOf("cosa" to "alba sole")),
        SScript.Call(ToolRegistry.SEARCH_TOOLS, mapOf("cosa" to "radar")),
        SScript.Text("ok"),
      ),
    )
    ask(provider, "un po' di tutto", config = AiOrchestratorConfig(maxSearchedTools = 2, searchLimit = 1, searchPreload = 0), conversation = conversation)
    assertEquals(listOf("meteo_sole", "meteo_radar"), conversation.loadedTools.toList())
  }

  @Test
  fun `la storia ripropone la testa dei risultati della domanda prima, non tutto`() {
    val conversation = Conversation(1L, 0L)
    val provider = SProvider(listOf(SScript.Call("nota_cerca"), SScript.Text("Trovata.")))
    ask(provider, "cerca la nota della spesa", conversation = conversation)
    val full = conversation.lastToolRound.filterIsInstance<Message.ToolResult>().single().content
    assertTrue(full.length > 2_000)
    // Come prima, se l'app lo chiede.
    val compacted = HistoryCompactor.compact(conversation, budgetTokens = 60_000)
    assertEquals(full, compacted.filterIsInstance<Message.ToolResult>().single().content)
    val next = SProvider(listOf(SScript.Text("ok")))
    ask(next, "e quella di ieri?", conversation = conversation)
    val replayed = next.streamed[0].messages.filterIsInstance<Message.ToolResult>().single().content
    assertTrue("riproposto ${replayed.length}", replayed.length <= 1_200)
    assertTrue(replayed, replayed.contains("nota_cerca: ok"))
  }

  @Test
  fun `gli schemi gia' mandati restano al loro posto da un giro all'altro`() {
    val provider = SProvider(listOf(SScript.Call(ToolRegistry.SEARCH_TOOLS, mapOf("cosa" to "luna")), SScript.Text("ok")))
    ask(provider, "luna?", config = AiOrchestratorConfig(searchPreload = 0))
    val first = provider.streamed[0].names()
    val second = provider.streamed[1].names()
    assertEquals(first.filter { it in second }, second.take(first.count { it in second }))
  }

  @Test
  fun `la ricerca, con accenti, parole vuote, radici e campi`() {
    val search = ToolSearch(tools)
    assertEquals(listOf("umidita"), ToolSearch.terms("l'umidità di oggi").filter { it == "umidita" })
    assertTrue(ToolSearch.matches("previsioni", "previsione"))
    assertFalse(ToolSearch.matches("pioggia", "piove"))
    assertEquals("meteo_luna", search.find("fase della luna").first().tool.name)
    assertTrue(search.find("fase della luna", exclude = setOf("meteo_luna")).none { it.tool.name == "meteo_luna" })
    assertTrue(search.find("zzz qqq").isEmpty())
    assertTrue(search.find("").isEmpty())
    // Le parole in piu' del gruppo trovano i suoi tool anche se nessuna descrizione le dice.
    val withTerms = ToolSearch(tools, mapOf(SGroup.METEO_ADESSO to listOf("ombrello")))
    assertEquals(SGroup.METEO_ADESSO, withTerms.find("serve l'ombrello?").first().tool.group)
  }
}

private enum class FlatGroup(override val id: String, override val statusKey: String, override val hint: String) : AiToolGroup {
  A("a", "a", "gruppo a"),
  B("b", "b", "gruppo b"),
}

/** Per le prove che leggono le chiamate del modello. */
@Suppress("unused")
private fun List<Message>.calls(): List<ToolCall> = filterIsInstance<Message.Assistant>().flatMap { it.toolCalls }
