package dev.antigravity.fluidengine.ai.tools

import dev.antigravity.fluidengine.ai.keys.AiSettings
import dev.antigravity.fluidengine.ai.net.RateLimitInfo
import dev.antigravity.fluidengine.ai.orchestrator.AiDiagnosticsLog
import dev.antigravity.fluidengine.ai.orchestrator.AiOrchestrator
import dev.antigravity.fluidengine.ai.orchestrator.AiOrchestratorConfig
import dev.antigravity.fluidengine.ai.orchestrator.AiRouter
import dev.antigravity.fluidengine.ai.orchestrator.AskInput
import dev.antigravity.fluidengine.ai.orchestrator.AskMode
import dev.antigravity.fluidengine.ai.orchestrator.AssistantState
import dev.antigravity.fluidengine.ai.orchestrator.Conversation
import dev.antigravity.fluidengine.ai.orchestrator.HistoryCompactor
import dev.antigravity.fluidengine.ai.provider.ChatDelta
import dev.antigravity.fluidengine.ai.provider.ChatProvider
import dev.antigravity.fluidengine.ai.provider.ChatRequest
import dev.antigravity.fluidengine.ai.provider.ChatTurn
import dev.antigravity.fluidengine.ai.provider.FinishReason
import dev.antigravity.fluidengine.ai.provider.Message
import dev.antigravity.fluidengine.ai.provider.ModelCatalogue
import dev.antigravity.fluidengine.ai.provider.ProviderId
import dev.antigravity.fluidengine.ai.provider.ReadyProvider
import dev.antigravity.fluidengine.ai.provider.ToolSpec
import dev.antigravity.fluidengine.ai.provider.TranscribeOptions
import dev.antigravity.fluidengine.ai.provider.Transcript
import dev.antigravity.fluidengine.ai.provider.Usage
import java.io.File
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/*
 * F9.2 - Il banco della ricerca dei tool, sul catalogo vero di PampAI (208 tool estratti dai sorgenti di
 * PampAI e delle cinque app collegate: artifacts/.../sessioni/f9-2/misure/estrattore.py), con il prompt
 * di sistema vero (prompt_sistema.py) e un modello finto che usa gli strumenti come farebbe un modello:
 * chiama il tool giusto se ce l'ha, altrimenti lo cerca, o apre la sua categoria. Il router finto e'
 * perfetto (sceglie sempre la categoria e il gruppo giusti, se li vede): "prima" e' il caso migliore di
 * oggi, e il risparmio misurato e' per difetto.
 *
 * Ogni richiesta mandata al modello finisce in build/f9-2/richieste-*.jsonl: lo script gettoni.py le conta
 * con i tokenizer veri. Qui si controlla la ricerca (il tool giusto trovato, gli altri non caricati di
 * serie) e si stima con i caratteri.
 */

private data class BenchCategory(override val id: String, override val label: String, override val hint: String) : AiToolCategory

private class BenchGroup(
  override val id: String,
  override val statusKey: String,
  override val hint: String,
  override val category: AiToolCategory?,
  override val loadsWithCategory: Boolean,
  val app: String,
) : AiToolGroup {
  override fun toString(): String = id
}

private class BenchTool(
  override val name: String,
  override val description: String,
  override val parameters: JsonObject,
  override val group: AiToolGroup,
  val app: String,
) : AiTool<Unit> {
  override suspend fun run(args: JsonObject, ctx: Unit): ToolOutput = ToolOutput(ToolText.limit(BenchOutputs.of(this)))
}

/** Risultati tipici, con la lunghezza dei veri (righe `chiave: valore`, scritte a mano come le scrivono i tool). */
private object BenchOutputs {
  private val meteo = buildString {
    appendLine("luogo: Firenze (Toscana)")
    listOf("ven 27" to "nuvoloso, 6-13 °C, pioggia 20% 0,4 mm, vento NE 12 km/h", "sab 28" to "pioggia, 8-12 °C, pioggia 80% 6,2 mm, vento S 18 km/h",
      "dom 29" to "variabile, 5-11 °C, pioggia 30% 0,8 mm, vento N 10 km/h", "lun 30" to "sereno, 2-10 °C, pioggia 0%, vento N 8 km/h",
      "mar 1" to "sereno, 1-11 °C, pioggia 0%, vento calmo", "mer 2" to "poco nuvoloso, 3-12 °C, pioggia 10%, vento E 6 km/h",
      "gio 3" to "nuvoloso, 5-12 °C, pioggia 40% 1,2 mm, vento SE 9 km/h").forEach { (day, text) -> appendLine("$day: $text; alba 07:22, tramonto 16:43") }
    appendLine("fonte: fusione di 3 servizi (Open-Meteo, MET Norway, ICON-D2), aggiornata alle 17:20")
  }.trim()
  private val voti = buildString {
    appendLine("materia: matematica (prof. Bianchi)")
    appendLine("media: 6,85 (ponderata 6,9) su 7 voti; minimo 5, massimo 8,5, ultimo 7+ il 2026-11-20 (ven)")
    appendLine("primo periodo: 6,85; scritti 6,6, orali 7,3")
    appendLine("voti: 2026-11-20 (ven) 7+ scritto; 2026-11-06 (ven) 6 orale; 2026-10-30 (ven) 8,5 scritto; 2026-10-16 (ven) 5 scritto; 2026-10-09 (ven) 7 orale; 2026-10-02 (ven) 6,5 scritto; 2026-09-26 (ven) 7,5 orale")
    appendLine("andamento: in salita nelle ultime tre settimane")
  }.trim()

  fun of(tool: BenchTool): String = when {
    tool.app == "meteo" -> meteo
    tool.app == "cv" -> voti
    tool.name == "email_prepara" -> "aperta l'app di posta con la bozza: a marco.rossi@example.com, oggetto \"Riunione\", testo pronto; l'utente la rilegge e la invia lui"
    tool.name.startsWith("musica_") -> "riproduco: playlist Studio (42 brani), primo brano \"Experience\" di Ludovico Einaudi; shuffle spento"
    tool.app == "bus" -> "fermata: Piazza Dalmazia (2041)\nlinea 23 per Viale Europa: 17:46 (fra 4 min, in tempo reale), 17:58, 18:10\nlinea 14 per Stazione: 17:51 (+2 min), 18:06"
    else -> "fatto: richiesta eseguita; dettagli: tutto in ordine"
  }
}

private data class BenchQuestion(val text: String, val keywords: String, val expected: Set<String>, val kind: String?)

private data class Recorded(val question: String, val variant: String, val kind: String, val request: ChatRequest)

/**
 * Il modello finto. Ha in mano la risposta giusta ([expected]) e fa quello che farebbe un modello che sa
 * cosa serve: se ha lo strumento lo chiama, se non ce l'ha lo cerca con [query] o apre la sua categoria; se
 * non lo trova risponde senza. Il router finto sceglie sempre la categoria e il gruppo giusti, se li vede.
 */
private class BenchModel(
  private val expected: Set<String>,
  private val query: String,
  private val catalogue: Map<String, BenchTool>,
  private val record: (String, ChatRequest) -> Unit,
) : ChatProvider {
  override val id: ProviderId = ProviderId.GROQ
  var called: String? = null

  override suspend fun complete(request: ChatRequest): ChatTurn {
    record("router", request)
    val schema = request.jsonSchema?.get("properties")?.jsonObject
    val categories = schema?.get("categoria")?.jsonObject?.get("enum")?.jsonArray?.map { it.jsonPrimitive.content }.orEmpty()
    val groups = schema?.get("sottocategorie")?.jsonObject?.get("items")?.jsonObject?.get("enum")?.jsonArray?.map { it.jsonPrimitive.content }.orEmpty()
    val target = expected.firstNotNullOfOrNull { catalogue[it] }
    val category = target?.group?.resolvedCategory?.id?.takeIf { it in categories } ?: AiRouter.NONE
    val group = target?.group?.id?.takeIf { category != AiRouter.NONE && it in groups }
    val verdict = buildJsonObject {
      put("categoria", category)
      put("sottocategorie", buildJsonArray { group?.let { add(JsonPrimitive(it)) } })
      put("profondo", false)
    }
    return ChatTurn(Message.Assistant(verdict.toString()), FinishReason.STOP, Usage(0, 0, 0), RateLimitInfo.EMPTY)
  }

  override fun stream(request: ChatRequest): Flow<ChatDelta> = flow {
    record("giro", request)
    val names = request.tools.map { it.name }.toSet()
    val lastUser = request.messages.indexOfLast { it is Message.User }
    val done = request.messages.drop(lastUser + 1)
    val results = done.filterIsInstance<Message.ToolResult>().map { it.name }
    fun enumOf(name: String, field: String): List<String> = request.tools.firstOrNull { it.name == name }
      ?.parameters?.get("properties")?.jsonObject?.get(field)?.jsonObject?.get("enum")?.jsonArray?.map { it.jsonPrimitive.content }.orEmpty()
    val target = expected.firstOrNull { it in names }
    val wanted = expected.firstNotNullOfOrNull { catalogue[it] }
    val call: Pair<String, JsonObject>? = when {
      expected.isEmpty() || results.any { it in expected } -> null
      target != null -> target to JsonObject(emptyMap())
      ToolRegistry.SEARCH_TOOLS in names && ToolRegistry.SEARCH_TOOLS !in results ->
        ToolRegistry.SEARCH_TOOLS to buildJsonObject { put("cosa", query) }
      wanted != null && ToolRegistry.OPEN_CATEGORY in names && ToolRegistry.OPEN_CATEGORY !in results &&
        wanted.group.resolvedCategory?.id in enumOf(ToolRegistry.OPEN_CATEGORY, "categoria") ->
        ToolRegistry.OPEN_CATEGORY to buildJsonObject { put("categoria", wanted.group.resolvedCategory!!.id) }
      wanted != null && ToolRegistry.OPEN_GROUP in names && wanted.group.id in enumOf(ToolRegistry.OPEN_GROUP, "sottocategoria") &&
        results.count { it == ToolRegistry.OPEN_GROUP } < 2 ->
        ToolRegistry.OPEN_GROUP to buildJsonObject { put("sottocategoria", wanted.group.id) }
      else -> null
    }
    if (call == null) {
      emit(ChatDelta.Text(ANSWER))
      emit(ChatDelta.Finish(FinishReason.STOP, Usage(0, 0, 0), RateLimitInfo.EMPTY))
    } else {
      if (call.first in expected) called = call.first
      emit(ChatDelta.ToolCallPart(0, "call_${done.size}", call.first, call.second.toString()))
      emit(ChatDelta.Finish(FinishReason.TOOL_CALLS, Usage(0, 0, 0), RateLimitInfo.EMPTY))
    }
  }

  override suspend fun listModels(): ModelCatalogue = ModelCatalogue(emptyList(), emptyList())
  override suspend fun transcribe(audio: File, mime: String, options: TranscribeOptions): Transcript = Transcript("", null)

  companion object {
    /** Una risposta di lunghezza tipica (le risposte restano nella storia della domanda dopo). */
    const val ANSWER = "Ecco cosa ho trovato: domani a Firenze sara' nuvoloso, con minime di 6 e massime di 13 gradi, poca pioggia " +
      "(20%) e vento debole da nord-est. Sabato invece arriva la pioggia, piu' forte nel pomeriggio: se esci porta l'ombrello."
  }
}

/**
 * Il pre-router di PampAI (`prompt/PreRouter.kt` e le regole delle app di `RegistryBuilder.preRules`, commit 9a435ae),
 * portato qui com'e': le stesse regex, gli stessi pesi, la stessa decisione (senza il vocabolario vivo delle app: le
 * materie e le fermate di Alessio non si leggono). Senza di lui "prima" chiamerebbe il router a ogni domanda, e PampAI
 * non lo fa: quando la domanda e' chiara i gruppi li decide lui, e il router non parte.
 */
private class PampaiPreRouter(groups: List<AiToolGroup>) {
  private class Rule(val group: AiToolGroup, pattern: String, val weight: Int = 2) {
    val regex = Regex(pattern)
  }

  private val byId = groups.associateBy { it.id }
  private fun g(id: String) = byId.getValue(id)

  private val rules: List<Rule> = listOf(
    Rule(g("aria"), "\\b(ricord\\w*|dimentic\\w*|memoria|memorizz\\w*|cosa sai di me|conversazion[ei] (passat|precedent|vecchi)\\w*|avevi detto|mi avevi|cosa (sai|puoi) fare|aiuto|consum[oi]|token|quanto ho speso|app collegat\\w*)\\b"),
    Rule(g("orologio"), "\\b(svegli[ae]|timer|conto alla rovescia|svegliami|allarm[ei])\\b"),
    Rule(g("promemoria"), "\\b(ricordami|promemoria|avvisami|ricordati di dirmi)\\b"),
    Rule(g("calendario"), "\\b(calendario|appuntament[oi]|riunion[ei]|event[oi]|impegn[oi] (di|del|per)|che ho (oggi|domani)|sono libero|libera)\\b"),
    Rule(g("contatti"), "\\b(chiam\\w*|telefon\\w*|contatt[oi]|numero di|messaggi[oa]?|whatsapp|sms|scrivi a|mail|email|condivid\\w*|manda a)\\b"),
    Rule(g("notifiche"), "\\b(notific\\w*|mi ha scritto|nuovi messaggi|cosa (e'|e|c e|c'e') arrivato)\\b"),
    Rule(g("notifiche"), "\\b(spotify|youtube|podcast|sta suonando su)\\b", 1),
    Rule(g("sistema"), "\\b(torcia|flash|volume|alza|abbassa|muto|silenzios[oa]|vibrazion[ei]|luminosit\\w*|schermo (piu|meno)|ruota|rotazion[ei]|non disturbare|dnd|suoneria)\\b"),
    Rule(g("apri"), "\\b(apri|aprimi|lancia|avvia|vai su|impostazioni (di|del)|app installat\\w*|quali app)\\b"),
    Rule(g("info"), "\\b(batteria|carica|autonomia|spazio|memoria del telefono|quanto spazio|dove sono|posizione|coordinate|che telefono|versione di android)\\b"),
    Rule(g("web"), "\\b(cerca|google|notizi[ae]|ultime|wikipedia|significa|definizion[ei]|cos'?e'?|cos e|chi e'|chi e|leggi (la pagina|il sito|questo link|l'articolo)|https?://|www\\.|sito)\\b"),
    Rule(g("calcolo"), "\\b(calcol\\w*|quanto fa|percentual\\w*|radice|potenza|converti|conversion[ei]|quanti (metri|chilometri|km|litri|grammi|chili|pollici|piedi|miglia|gradi)|in (euro|dollari|sterline|yen|franchi)|cambio|valut[ae]|fuso|che ore sono a|che ora e' a|quanti giorni|quante settimane|che giorno (era|sara'|sara)|fra quanto|mancano)\\b"),
    Rule(g("schermo"), "\\b(schermo|sullo schermo|questa pagina|questa schermata|cosa c'?e'? scritto|cosa vedi|cosa (e'|e) questo|traduci (questo|quello che|lo schermo|la pagina)|riassumi (questo|questa|lo schermo)|quest[ao] app)\\b"),
    Rule(g("riproduzione"), "\\b(metti (su|la musica|un brano|una canzone|qualcosa|un po' di|della musica|un pezzo)|suona|riproduci|play|pausa|ferma la musica|salta|prossim[ao] (brano|canzone)|canzone|brano|musica|volume della musica|shuffle|casuale|ripeti|radio|salva (il brano|la canzone)|cosa sta suonando|che canzone e')\\b"),
    Rule(g("libreria"), "\\b(playlist|ascoltat[oi] di recente|recenti|scaricat[oi]|download|fluidify|dispositiv[oi]|cerca (il brano|la canzone|un brano|un artista)|artista|album)\\b"),
    // Le app collegate (RegistryBuilder.preRules): le parole del mestiere sul gruppo che si apre con la categoria, e il nome.
    Rule(g("cv_voti"), "\\b(vot[oi]|media|medie|compit[oi]|verific\\w*|interrogazion\\w*|prof\\w*|scuola|scolastic\\w*|registro|circolar\\w*|bacheca|assenz\\w*|giustific\\w*|materia|materie|lezion[ei]|pagella|classe)\\b"),
    Rule(g("meteo_orario"), "\\b(meteo|che tempo|tempo fa|piov\\w*|piogg\\w*|nevic\\w*|neve|temperatur\\w*|grad[oi]|vento|umidit\\w*|nuvol\\w*|prevision[ei]|radar|allert[ae]|ombrello|tramonto|alba)\\b"),
    Rule(g("bus_orari"), "\\b(bus|autobus|pullman|fermata|fermate|passagg\\w*|corsa|corse|tram|quando passa|come arrivo|come ci arrivo|capolinea)\\b"),
    Rule(g("convert_convert"), "\\b(conversion[ei] di file|formato del file|in pdf|in jpg|in png|in mp3|in mp4|in docx|da pdf a)\\b"),
    Rule(g("cv_voti"), "\\b(classeviva expressive|cv)\\b", 1),
    Rule(g("meteo_orario"), "\\b(fluid weather|meteo)\\b", 1),
    Rule(g("bus_orari"), "\\b(fluid transit|bus)\\b", 1),
    Rule(g("convert_convert"), "\\b(convert to it|convert)\\b", 1),
    Rule(g("fluidify_music_data"), "\\b(fluidify)\\b", 1),
  )

  class Verdict(val groups: Set<AiToolGroup>, val confident: Boolean)

  private fun normalize(text: String): String {
    val decomposed = java.text.Normalizer.normalize(text.lowercase(java.util.Locale.ITALIAN), java.text.Normalizer.Form.NFD)
    return decomposed.replace(Regex("\\p{M}+"), "").replace(Regex("[^a-z0-9/'\\-.:]+"), " ").trim().replace(Regex(" +"), " ")
  }

  fun decide(question: String): Verdict {
    val text = normalize(question)
    val scores = linkedMapOf<AiToolGroup, Int>()
    rules.forEach { rule ->
      val hits = rule.regex.findAll(text).count()
      if (hits > 0) scores[rule.group] = (scores[rule.group] ?: 0) + rule.weight * hits
    }
    if (scores.isEmpty()) return Verdict(emptySet(), confident = false)
    val ranked = scores.entries.sortedByDescending { it.value }
    val top = ranked.first()
    val second = ranked.getOrNull(1)
    val confident = top.value >= 2 && (second == null || second.value == 0)
    return Verdict(if (confident) setOf(top.key) else ranked.map { it.key }.take(4).toSet(), confident)
  }
}

class ToolSearchBenchTest {

  private val json = Json { ignoreUnknownKeys = true }

  private fun resource(name: String): String =
    requireNotNull(javaClass.classLoader!!.getResource("f9-2/$name")) { "manca la risorsa f9-2/$name" }.readText(Charsets.UTF_8)

  /** Il catalogo di PampAI dalla risorsa, nell'ordine di RegistryBuilder. */
  private class Catalog(val tools: List<BenchTool>, val groups: List<BenchGroup>, val categories: List<BenchCategory>)

  private val catalog: Catalog by lazy {
    val root = json.parseToJsonElement(resource("catalogo-pampai.json")).jsonObject
    val categories = root["categorie"]!!.jsonArray.map { it.jsonObject }.associate { c ->
      c["id"]!!.jsonPrimitive.content to BenchCategory(c["id"]!!.jsonPrimitive.content, c["label"]!!.jsonPrimitive.content, c["hint"]!!.jsonPrimitive.content)
    }
    val groups = root["gruppi"]!!.jsonArray.map { it.jsonObject }.map { g ->
      BenchGroup(
        g["id"]!!.jsonPrimitive.content, g["statusKey"]!!.jsonPrimitive.content, g["hint"]!!.jsonPrimitive.content,
        categories[g["categoria"]!!.jsonPrimitive.content], g["loadsWithCategory"]!!.jsonPrimitive.booleanOrNull == true, g["app"]!!.jsonPrimitive.content,
      )
    }
    val byId = groups.associateBy { it.id }
    val tools = root["tool"]!!.jsonArray.map { it.jsonObject }.map { t ->
      BenchTool(t["name"]!!.jsonPrimitive.content, t["description"]!!.jsonPrimitive.content, t["parameters"]!!.jsonObject,
        byId.getValue(t["gruppo"]!!.jsonPrimitive.content), t["app"]!!.jsonPrimitive.content)
    }
    // Solo i gruppi con dei tool, come declaredGroups di PampAI.
    val used = groups.filter { g -> tools.any { it.group === g } }
    Catalog(tools, used, categories.values.filter { c -> used.any { it.category == c } })
  }

  private val questions: List<BenchQuestion> by lazy {
    json.parseToJsonElement(resource("banco-domande.json")).jsonObject["domande"]!!.jsonArray.map { it.jsonObject }.map { q ->
      BenchQuestion(q["d"]!!.jsonPrimitive.content, q["k"]!!.jsonPrimitive.content, q["atteso"]!!.jsonArray.map { it.jsonPrimitive.content }.toSet(),
        (q["tipo"] as? JsonPrimitive)?.contentOrNull)
    }
  }

  /** Le parole del mestiere di PampAI (RegistryBuilder.DOMAIN_WORDS), da regex a parole: la ricerca le usa come riga del gruppo. */
  private val tradeWords = mapOf(
    "cv" to "voti media medie compiti verifiche interrogazioni professore prof scuola scolastico registro circolari bacheca assenze giustificazioni materia materie lezioni pagella classe",
    "meteo" to "meteo tempo piove piovera pioggia nevica neve temperatura gradi vento umidita nuvole nuvoloso previsioni radar allerta ombrello tramonto alba freddo caldo",
    "bus" to "bus autobus pullman fermata fermate passaggi corsa corse tram passa arrivo capolinea mezzi",
    "convert" to "conversione converti trasforma formato formati file pdf jpg png mp3 mp4 docx word heic",
    "fluidify" to "spotify ascoltati ascolti storia playlist artisti brani",
  )

  private val homePrompt: String by lazy { resource("prompt-sistema.txt") }

  /** Il prompt di dopo: "App e aree" con le sole aree di PampAI, e una riga per le altre (il piano per PampAI, F9.2). */
  private val afterPrompt: String by lazy {
    val home = catalog.categories.filter { c -> catalog.groups.any { it.category == c && it.app == "pampai" } }
    val summary = home.joinToString("; ") { c -> "${c.label} (${c.id}: ${catalog.groups.filter { it.category == c }.joinToString(", ") { it.id }})" }
    val others = catalog.categories.filter { it !in home }.joinToString(", ") { it.label }
    homePrompt.lines().joinToString("\n") { line ->
      if (line.startsWith("App e aree: ")) "App e aree: $summary\nAltre app (le trovi con cerca_strumenti): $others" else line
    }
  }

  /**
   * Prima: PampAI di oggi. Dopo: l'engine 4.0 col solo cambio minimo in PampAI (le altre app differite, "App e aree"
   * di casa). Senza router: in piu' `skipRouter`. Piano: in piu' il prompt di sistema corto proposto nel piano per PampAI.
   */
  private enum class Setup(val label: String) { BEFORE("prima"), AFTER("dopo"), AFTER_NO_ROUTER("dopo_senza_router"), PLAN("dopo_piano") }

  private val shortPrompt: String by lazy { resource("prompt-sistema-breve.txt") }

  private fun registry(setup: Setup): ToolRegistry<Unit> {
    if (setup == Setup.BEFORE) return ToolRegistry(catalog.tools, catalog.groups)
    val deferred = catalog.groups.filter { it.app != "pampai" }.toSet()
    val terms: Map<AiToolGroup, List<String>> = deferred.associateWith { g -> tradeWords[(g as BenchGroup).app]?.split(' ').orEmpty() }
    return ToolRegistry(catalog.tools, catalog.groups, deferred = deferred, searchTerms = terms)
  }

  private fun router(registry: ToolRegistry<Unit>) = AiRouter(
    groups = registry.homeGroups,
    actionGroup = null,
    domainHint = "un assistente personale, Aria, che controlla il telefono, cerca sul web e usa le app Pampa collegate",
    defaultGroups = registry.homeGroups.filter { it.id == "aria" },
    categories = registry.categories,
  )

  private class Run(val found: Boolean, val requests: List<Recorded>)

  private fun ask(setup: Setup, question: BenchQuestion, useKeywords: Boolean, conversation: Conversation = Conversation(1L, 0L), sink: MutableList<Recorded>): Run {
    val registry = registry(setup)
    val variant = setup.label + if (useKeywords) "_k" else "_d"
    val recorded = mutableListOf<Recorded>()
    val model = BenchModel(question.expected, if (useKeywords) question.keywords else question.text, catalog.tools.associateBy { it.name }) { kind, request ->
      recorded += Recorded(question.text, variant, kind, request)
    }
    val config = if (setup == Setup.BEFORE) AiOrchestratorConfig(historyToolResultChars = Int.MAX_VALUE) else AiOrchestratorConfig()
    val orchestrator = AiOrchestrator(registry, router(registry), AiDiagnosticsLog(), config = config)
    val pre = PampaiPreRouter(registry.groups).decide(question.text)
    val input = AskInput(
      question = question.text,
      mode = AskMode.TEXT,
      language = "it",
      settings = AiSettings(),
      providers = listOf(ReadyProvider(model, "chat", "stt", "router", deepModel = "profondo")),
      toolContext = Unit,
      systemPrompt = when (setup) {
        Setup.BEFORE -> homePrompt
        Setup.PLAN -> shortPrompt
        else -> afterPrompt
      },
      conversation = conversation,
      actionsEnabled = true,
      skipRouter = setup == Setup.AFTER_NO_ROUTER || setup == Setup.PLAN,
      // Come AssistantEngine: sicuro -> i gruppi decisi; incerto -> suggerimento al router (o alla ricerca).
      preselectedGroups = pre.groups.takeIf { pre.confident && it.isNotEmpty() },
      routerHint = if (pre.confident) emptySet() else pre.groups,
    )
    runBlocking { orchestrator.ask(input, MutableStateFlow(AssistantState.Idle)) }
    sink += recorded
    return Run(question.expected.isEmpty() || model.called != null, recorded)
  }

  private fun chars(request: ChatRequest): Int =
    request.messages.sumOf { m ->
      when (m) {
        is Message.System -> m.text.length
        is Message.User -> m.text.length
        is Message.Assistant -> (m.text?.length ?: 0) + m.toolCalls.sumOf { it.name.length + it.arguments.toString().length }
        is Message.ToolResult -> m.content.length
      }
    } + request.tools.sumOf { specChars(it) } + (request.jsonSchema?.toString()?.length ?: 0)

  private fun specChars(spec: ToolSpec): Int = spec.name.length + spec.description.length + spec.parameters.toString().length

  @Test
  fun `la ricerca trova il tool giusto, e le altre app non sono caricate di serie`() {
    val sink = mutableListOf<Recorded>()
    val summary = buildJsonObject {
      for (setup in Setup.entries) for (keywords in listOf(false, true)) {
        if (setup == Setup.BEFORE && keywords) continue
        val runs = questions.map { it to ask(setup, it, keywords, sink = sink) }
        val withTool = runs.filter { it.first.expected.isNotEmpty() }
        val found = withTool.count { it.second.found }
        val missed = withTool.filter { !it.second.found }.map { it.first.text }
        put(setup.label + if (keywords) "_k" else "_d", buildJsonObject {
          put("domande", withTool.size)
          put("trovate", found)
          put("mancate", buildJsonArray { missed.forEach { add(JsonPrimitive(it)) } })
          put("richieste", runs.sumOf { it.second.requests.size })
          put("caratteri", runs.sumOf { r -> r.second.requests.sumOf { chars(it.request) } })
          val typical = runs.filter { it.first.kind != null }
          put("caratteri_cinque_tipo", typical.sumOf { r -> r.second.requests.sumOf { chars(it.request) } })
        })
      }
    }
    write("sommario-banco.json", summary.toString())
    // La ricerca da sola, domanda per domanda: i primi tre e il loro punteggio (per tarare il precaricamento).
    val search = registry(Setup.AFTER).search
    write("ricerca-banco.tsv", questions.joinToString("\n") { q ->
      val raw = search.find(q.text, 3).joinToString(" | ") { "${it.tool.name} ${"%.2f".format(java.util.Locale.ROOT, it.score)}" }
      val keys = search.find(q.keywords, 3).joinToString(" | ") { "${it.tool.name} ${"%.2f".format(java.util.Locale.ROOT, it.score)}" }
      "${q.text}\t${q.expected.joinToString(",")}\t$raw\t$keys"
    })
    write("richieste-banco.jsonl", sink.joinToString("\n") { line(it) })

    fun rate(key: String): Double = summary[key]!!.jsonObject.let { it["trovate"]!!.jsonPrimitive.content.toDouble() / it["domande"]!!.jsonPrimitive.content.toDouble() }
    // Prima, col router perfetto, si trova tutto: e' il riferimento.
    assertEquals(1.0, rate("prima_d"), 0.0)
    // Con la ricerca: le parole chiave di un modello trovano quasi tutto, la domanda grezza un po' meno.
    assertTrue("dopo_k ${rate("dopo_k")} ${summary["dopo_k"]}", rate("dopo_k") >= 0.95)
    assertTrue("dopo_d ${rate("dopo_d")} ${summary["dopo_d"]}", rate("dopo_d") >= 0.85)
    assertTrue("dopo_senza_router_k ${summary["dopo_senza_router_k"]}", rate("dopo_senza_router_k") >= 0.95)
    // E si risparmia sulle cinque domande tipo.
    val before = summary["prima_d"]!!.jsonObject["caratteri_cinque_tipo"]!!.jsonPrimitive.content.toInt()
    val after = summary["dopo_k"]!!.jsonObject["caratteri_cinque_tipo"]!!.jsonPrimitive.content.toInt()
    assertTrue("prima $before, dopo $after", after < before * 0.92)
    val plan = summary["dopo_piano_k"]!!.jsonObject["caratteri_cinque_tipo"]!!.jsonPrimitive.content.toInt()
    assertTrue("prima $before, piano $plan", plan < before * 0.65)
  }

  @Test
  fun `una domanda generica non carica gli strumenti delle altre app`() {
    val sink = mutableListOf<Recorded>()
    val generic = questions.single { it.kind == "generica" }
    val run = ask(Setup.AFTER, generic, useKeywords = true, sink = sink)
    val first = run.requests.first { it.kind == "giro" }.request
    val names = first.tools.map { it.name }
    val remote = listOf("cv_", "meteo_", "bus_", "convert_", "fluidify_")
    assertTrue(names.toString(), names.none { n -> remote.any { n.startsWith(it) } })
    assertTrue(names.toString(), ToolRegistry.SEARCH_TOOLS in names)
    // Il router di dopo non vede le altre app.
    val routerCall = run.requests.first { it.kind == "router" }.request
    val prompt = (routerCall.messages.first() as Message.System).text
    assertFalse(prompt, prompt.contains("ClasseViva"))
    assertFalse(prompt, prompt.contains("cv_voti"))
    // apri_categoria elenca solo le aree di PampAI.
    val open = first.tools.firstOrNull { it.name == ToolRegistry.OPEN_CATEGORY }
    open?.let { assertFalse(it.description, it.description.contains("ClasseViva")) }
  }

  @Test
  fun `le cinque domande tipo e il seguito, per la tabella dei gettoni`() {
    val sink = mutableListOf<Recorded>()
    val typical = questions.filter { it.kind != null }
    for (setup in Setup.entries) {
      for (q in typical) ask(setup, q, useKeywords = true, sink = sink)
      // Il seguito: "e sabato?" dopo la domanda sul meteo, con la storia e il traffico tool della domanda prima.
      val conversation = Conversation(2L, 0L)
      val meteo = typical.single { it.kind == "meteo" }
      ask(setup, meteo, useKeywords = true, conversation = conversation, sink = mutableListOf())
      ask(setup, BenchQuestion("E sabato?", "previsioni meteo sabato", meteo.expected, "seguito"), useKeywords = true, conversation = conversation, sink = sink)
    }
    write("richieste-tipo.jsonl", sink.joinToString("\n") { line(it) })
    // Il seguito di dopo rimanda meno storia: i risultati di prima tornano con la loro testa.
    val follow = sink.filter { it.question == "E sabato?" && it.kind == "giro" }
    val beforeReplay = follow.first { it.variant.startsWith("prima") }.request.messages.filterIsInstance<Message.ToolResult>().first().content.length
    val afterReplay = follow.first { it.variant.startsWith("dopo_k") }.request.messages.filterIsInstance<Message.ToolResult>().first().content.length
    assertTrue("prima $beforeReplay, dopo $afterReplay", afterReplay <= 1_200 && afterReplay <= beforeReplay)
  }

  /** Una richiesta registrata in una riga JSON, con le parti etichettate per gettoni.py. */
  private fun line(r: Recorded): String {
    val messages = r.request.messages
    val lastUser = messages.indexOfLast { it is Message.User && it.text == r.question }
    val array = buildJsonArray {
      messages.forEachIndexed { index, m ->
        add(buildJsonObject {
          val part = when {
            r.kind == "router" && m is Message.System -> "router"
            r.kind == "router" -> "domanda"
            index == 0 && m is Message.System -> "sistema"
            m is Message.System -> "sistema_finale"
            index < lastUser -> "storia"
            index == lastUser -> "domanda"
            m is Message.ToolResult -> "risultati"
            else -> "chiamate"
          }
          put("part", part)
          put("role", m::class.simpleName!!.lowercase())
          put("text", when (m) {
            is Message.System -> m.text
            is Message.User -> m.text
            is Message.Assistant -> m.text ?: ""
            is Message.ToolResult -> m.content
          })
          if (m is Message.Assistant && m.toolCalls.isNotEmpty()) {
            put("calls", buildJsonArray { m.toolCalls.forEach { c -> add(buildJsonObject { put("name", c.name); put("arguments", c.arguments) }) } })
          }
        })
      }
    }
    return buildJsonObject {
      put("question", r.question)
      put("variant", r.variant)
      put("kind", r.kind)
      put("call", r.request.hashCode())
      put("messages", array)
      put("tools", buildJsonArray { r.request.tools.forEach { t -> add(buildJsonObject { put("name", t.name); put("description", t.description); put("parameters", t.parameters) }) } })
      r.request.jsonSchema?.let { put("jsonSchema", it) }
      put("estimate", HistoryCompactor.estimateTokens(messages) + r.request.tools.sumOf { specChars(it) } / 3)
    }.toString()
  }

  private fun write(name: String, text: String) {
    val dir = File("build/f9-2").apply { mkdirs() }
    File(dir, name).writeText(text, Charsets.UTF_8)
  }
}

