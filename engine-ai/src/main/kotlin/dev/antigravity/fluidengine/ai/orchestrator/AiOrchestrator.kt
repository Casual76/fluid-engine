package dev.antigravity.fluidengine.ai.orchestrator

import dev.antigravity.fluidengine.ai.keys.AiSettings
import dev.antigravity.fluidengine.ai.keys.ThinkingLevel
import dev.antigravity.fluidengine.ai.net.AiError
import dev.antigravity.fluidengine.ai.net.BadReason
import dev.antigravity.fluidengine.ai.net.RateLimitInfo
import dev.antigravity.fluidengine.ai.net.string
import dev.antigravity.fluidengine.ai.provider.ChatDelta
import dev.antigravity.fluidengine.ai.provider.ChatRequest
import dev.antigravity.fluidengine.ai.provider.ContentPart
import dev.antigravity.fluidengine.ai.provider.FinishReason
import dev.antigravity.fluidengine.ai.provider.Message
import dev.antigravity.fluidengine.ai.provider.ModelCapabilities
import dev.antigravity.fluidengine.ai.provider.ModelTier
import dev.antigravity.fluidengine.ai.provider.ProviderId
import dev.antigravity.fluidengine.ai.provider.ReadyProvider
import dev.antigravity.fluidengine.ai.provider.ReasoningLevel
import dev.antigravity.fluidengine.ai.provider.ToolCall
import dev.antigravity.fluidengine.ai.provider.ToolCallAssembler
import dev.antigravity.fluidengine.ai.provider.ToolChoice
import dev.antigravity.fluidengine.ai.provider.ToolSpec
import dev.antigravity.fluidengine.ai.provider.Usage
import dev.antigravity.fluidengine.ai.provider.displayName
import dev.antigravity.fluidengine.ai.tools.AiToolCategory
import dev.antigravity.fluidengine.ai.tools.AiToolGroup
import dev.antigravity.fluidengine.ai.tools.ToolOutput
import dev.antigravity.fluidengine.ai.tools.ToolRegistry
import dev.antigravity.fluidengine.ai.tools.ToolText
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.serialization.json.JsonElement

/** Cio' che una domanda produce, oltre allo stato: la risposta e la sua traccia. */
data class AskResult(
  val answer: String,
  val chips: List<AnswerChip>,
  val provider: ProviderId,
  val usage: Usage?,
  val toolsUsed: List<String>,
  val log: AiRequestLog,
  val tierReached: ModelTier,
)

/**
 * Cio' che serve all'orchestratore per una domanda, preparato dall'app: il contesto dei tool
 * ([C]), il prompt, la conversazione, e i ganci con cui l'app orienta il giro senza che l'engine
 * conosca il suo dominio.
 */
class AskInput<C>(
  val question: String,
  val mode: AskMode,
  val language: String,
  val settings: AiSettings,
  val providers: List<ReadyProvider>,
  val toolContext: C,
  val systemPrompt: String,
  val conversation: Conversation,
  val actionsEnabled: Boolean,
  val forceFinalPrompt: String = AiPrompts.forceFinal(language),
  /** I gruppi decisi da un pre-router locale: se non e' null, lo stadio 1 non parte. */
  val preselectedGroups: Set<AiToolGroup>? = null,
  /** I gruppi che il pre-router locale ritiene probabili, da suggerire allo stadio 1. */
  val routerHint: Set<AiToolGroup> = emptySet(),
  /** L'app sa gia' che servira' il livello profondo (un allegato citato, una domanda lunga). */
  val deepRequested: Boolean = false,
  /** Quali chip `[[id]]` l'app riconosce: gli altri spariscono dal testo e basta. */
  val chipFilter: (AnswerChip) -> Boolean = { true },
  /**
   * Quando un tool porta un allegato che il modello non regge, l'app puo' tradurlo in testo
   * (estrarre un PDF, descrivere un'immagine); null se non sa farlo.
   */
  val attachmentFallback: suspend (ContentPart) -> String? = { null },
  /**
   * Le parti che l'utente ha messo nella domanda (1.26.0): uno screenshot, una foto, un PDF. Vanno
   * al modello nel suo messaggio se lo regge; se le regge solo il livello profondo si parte da li';
   * se non le regge nessuno passano da [attachmentFallback], come gli allegati dei tool.
   */
  val attachments: List<ContentPart> = emptyList(),
  /**
   * Restare sul servizio scelto (1.29.0): nessun cambio di provider, mai, anche se in [providers]
   * ce ne sono altri. Un 429 aspetta il `retry-after` (al massimo due volte, entro il budget) e
   * poi la domanda fallisce con [FailureKind.RATE_LIMITED]; un 5xx o la rete riprovano una volta
   * e poi falliscono. Lo stadio 1 che fallisce non fa fallire la domanda: si prosegue col ripiego
   * del router ([routerHint] e i gruppi di prima). E' il contrario della riserva: chi ha scelto
   * Gemini vuole Gemini, non "chi risponde per primo" — e senza questo un 429 sul profondo di
   * Gemini portava ogni domanda complessa sul profondo di OpenRouter, qualunque cosa avesse
   * scelto l'utente.
   */
  val pinProvider: Boolean = false,
  /**
   * Un provider ha detto che un modello non c'e' piu' (2.8.0): ritirato, rinominato, senza endpoint
   * per questa chiave. Riceve il provider e il modello, per qualunque livello (anche il router), e
   * arriva prima della decisione di failover. L'app ci riallinea le scelte salvate — di solito con
   * `AiKeyVerifier.markUnavailable` — cosi' la domanda dopo non ci ribatte contro. Non sospende:
   * un lavoro lungo l'app lo lancia nel suo scope.
   */
  val onModelUnavailable: (ProviderId, String) -> Unit = { _, _ -> },
)

/** I numeri dell'orchestratore, tutti in un posto: l'app li alza o li abbassa per il suo caso. */
data class AiOrchestratorConfig(
  val maxRounds: Int = 8,
  val maxMoreTools: Int = 2,
  val maxOutputTokens: Int = 1_500,
  val parallelTools: Int = 4,
  val toolTimeoutMillis: Long = 30_000L,
  val totalBudgetMillis: Long = 240_000L,
  val finalReserveMillis: Long = 20_000L,
  val historyBudgetGroq: Int = 5_000,
  val historyBudgetOther: Int = 60_000,
  /** Oltre questi caratteri di risultati dei tool si passa al livello profondo, se c'e'. */
  val escalationChars: Int = 12_000,
  /** Il tetto di un singolo risultato di tool: l'ultimo argine, sopra a quello che si da' il tool. */
  val toolTextChars: Int = ToolText.MAX_CHARS,
  /** Quanto testo di un allegato tradotto dall'app puo' entrare nel giro. */
  val attachmentTextChars: Int = 12_000,
  val publishMinChars: Int = 24,
  val publishMinMillis: Long = 150L,
  val temperature: Double = 0.3,
  /**
   * Catalogo gerarchico (1.26.0): quanti tool possono restare aperti in una conversazione. Oltre,
   * cadono i gruppi usati meno di recente. Ottanta strumenti sono cinque-seimila token di schema:
   * il tetto di quello che un giro su Groq gratuito regge.
   */
  val maxLoadedTools: Int = 80,
  /** Quante categorie o sottocategorie il modello puo' aprire da se' in una domanda. */
  val maxOpens: Int = 4,
  /**
   * La pazienza di un giro che non pensa (2.8.0): il silenzio massimo fra due pezzi dello stream,
   * e l'attesa della risposta intera nella riprova senza stream.
   */
  val streamChunkTimeoutMillis: Int = 45_000,
  /**
   * La pazienza di un giro che pensa o lavora col profondo (2.8.0). Il thinking di Gemini tace anche
   * oltre mezzo minuto prima del primo pezzo: con i trenta secondi di prima quel silenzio era un
   * timeout, poi una riprova, poi un fallimento TIMEOUT su una domanda che stava andando benissimo.
   */
  val thinkingChunkTimeoutMillis: Int = 120_000,
) {

  /**
   * Il timeout di lettura di un giro (2.8.0): [thinkingChunkTimeoutMillis] se il modello pensa
   * ([reasoning] diverso da NONE) o il livello e' il profondo, [streamChunkTimeoutMillis] altrimenti.
   *
   * E si misura sul budget. [TimeBudget] si controlla solo fra un giro e l'altro — uno stream gia'
   * partito lo ferma soltanto questo timeout — quindi con una card da novanta secondi un silenzio da
   * centoventi sforerebbe di mezzo minuto. Il timeout lungo vale finche' il budget ha quel tempo;
   * sotto, il timeout e' quel che resta del budget, e mai meno di quello corto: un giro partito a
   * budget quasi finito aspetta quanto uno normale, invece di morire subito. Col budget di default
   * (240 s) il profondo ha i suoi centoventi secondi pieni; un'app che vuole lasciar pensare a lungo
   * una card a schermo alza [totalBudgetMillis].
   */
  fun readTimeoutFor(tier: ModelTier, reasoning: ReasoningLevel, budgetRemainingMillis: Long): Int {
    val wanted = if (tier == ModelTier.DEEP || reasoning != ReasoningLevel.NONE) thinkingChunkTimeoutMillis else streamChunkTimeoutMillis
    val room = budgetRemainingMillis.coerceIn(0L, Int.MAX_VALUE.toLong()).toInt()
    return minOf(wanted, maxOf(streamChunkTimeoutMillis, room))
  }
}

/**
 * Il cervello: stadio 1 (gruppi, o il pre-router dell'app) -> giro dei tool in stream con
 * esecuzione parallela, budget di tempo e di giri, cambio di provider sui 429 e sui 5xx, attesa
 * col conto alla rovescia quando non c'e' nessuno a cui passare. In piu', rispetto al primo
 * orchestratore: tre livelli di modello con l'escalation al profondo quando un tool porta un
 * allegato o i risultati si fanno lunghi, e la riprova senza stream quando il flusso si spezza a
 * meta'. Tutto lo stato osservabile passa da [ask]'s `state`.
 *
 * Con un catalogo gerarchico ([ToolRegistry.hierarchical]) lo stadio 1 sceglie una categoria e le
 * sue sottocategorie, il modello ne apre altre con `apri_categoria` e `apri_sottocategoria`, e
 * quello che e' stato aperto resta nella [Conversation] per le domande dopo.
 *
 * Dalla 1.29.0 il cambio di provider si puo' spegnere ([AskInput.pinProvider]: si aspetta, si
 * riprova, o si fallisce, mai si migra), e quando invece avviene la riserva **riparte dalla chat**
 * e non eredita il profondo di prima ([tierAfterSwitch]). Ogni modello che ha risposto finisce,
 * in ordine, in [AiRequestLog.modelsUsed].
 *
 * Dalla 2.8.0 un 400 si legge ([dev.antigravity.fluidengine.ai.net.BadReason]): un `tool_use_failed`
 * si riprova, un modello sparito si dice all'app ([AskInput.onModelUnavailable]) e si passa oltre,
 * una richiesta troppo lunga si accorcia una volta ([FailoverDecision.TrimAndRetry]). Una risposta
 * finale vuota non e' mai un successo. Ogni cambio di provider ha il suo perche'
 * ([AiRequestLog.switches], [AssistantState.SwitchingProvider.reason]), e il timeout di lettura di
 * un giro dipende da cosa si chiede ([AiOrchestratorConfig.readTimeoutFor]).
 */
class AiOrchestrator<C>(
  private val registry: ToolRegistry<C>,
  private val router: AiRouter,
  private val diagnostics: AiDiagnosticsLog,
  private val failover: FailoverPolicy = FailoverPolicy(),
  private val config: AiOrchestratorConfig = AiOrchestratorConfig(),
  private val clock: () -> Long = System::currentTimeMillis,
  /** Dove versare un evento per ogni chiamata a un provider; null = nessuno lo vuole. */
  private val usageSink: AiUsageSink? = null,
) {

  /**
   * Lo stato del failover di una domanda. [retries], [trims] ed [emptyRetries] valgono per il
   * provider corrente e ripartono da zero a ogni cambio; [switches] tiene la storia per il log.
   */
  private class Attempt(
    var provider: ReadyProvider,
    val switched: MutableList<ProviderId> = mutableListOf(),
    var waits: Int = 0,
    var retries: Int = 0,
    var trims: Int = 0,
    var emptyRetries: Int = 0,
    val switches: MutableList<ProviderSwitch> = mutableListOf(),
  )

  /** Com'e' finito un giro, e chi l'ha fatto davvero: dopo un cambio di provider [tier] e [model] possono differire da quelli chiesti. */
  private class TurnOutcome(val text: String?, val calls: List<ToolCall>, val raw: JsonElement?, val usage: Usage?, val rateLimit: RateLimitInfo, val finish: FinishReason, val tier: ModelTier, val model: String)

  private class ToolRun(val call: ToolCall, val output: ToolOutput)

  /** Il messaggio dell'utente com'e' pronto per il modello, e il livello con cui partire. */
  private class UserTurn(val parts: List<ContentPart>, val tier: ModelTier)

  val maxRounds: Int get() = config.maxRounds

  suspend fun ask(input: AskInput<C>, state: MutableStateFlow<AssistantState>): AskResult {
    val startedAt = clock()
    val budget = TimeBudget(startedAt, config.totalBudgetMillis, config.finalReserveMillis, clock)
    val question = input.question
    val conversation = input.conversation
    if (input.providers.isEmpty()) throw AssistantFailure(FailureKind.NO_KEYS, null)
    val attempt = Attempt(input.providers.first())
    val toolTraces = mutableListOf<ToolTrace>()
    var usageTotal: Usage? = null
    var waitedSeconds = 0
    var routerUsed = false
    var lastRateLimit: RateLimitInfo? = null
    var tier = if (input.deepRequested) ModelTier.DEEP else ModelTier.CHAT
    // Gli allegati dell'utente decidono il livello prima di tutto: uno screenshot che vede solo
    // il modello profondo fa partire il giro da li'.
    val userTurn = prepareUserTurn(input, attempt.provider, tier)
    tier = userTurn.tier
    var tierReached = tier
    val modelsUsed = linkedMapOf<ModelTier, String>()
    // Tutti i modelli che hanno risposto, in ordine, senza ripetere chi risponde due giri di fila.
    val used = mutableListOf<ModelUse>()
    fun use(provider: ProviderId, usedTier: ModelTier, model: String) {
      val entry = ModelUse(provider, usedTier, model)
      if (used.lastOrNull() != entry) used += entry
    }
    val hierarchical = registry.hierarchical

    // Stadio 1: i gruppi. Il pre-router dell'app, se ha deciso, vince; su OpenRouter il catalogo
    // piatto va intero (una chiamata in meno sul tetto giornaliero), quello gerarchico no: li'
    // valgono i gruppi gia' aperti e il suggerimento del pre-router, e il modello apre il resto.
    val preselected = input.preselectedGroups?.filter { input.actionsEnabled || it != registry.actionGroup }?.toSet()
    var groups: Set<AiToolGroup> = when {
      attempt.provider.provider.id == ProviderId.OPENROUTER && !hierarchical -> allGroups(input)
      !preselected.isNullOrEmpty() -> preselected
      attempt.provider.provider.id == ProviderId.OPENROUTER -> input.routerHint.filter { input.actionsEnabled || it != registry.actionGroup }.toSet()
      else -> {
        state.value = AssistantState.Classifying(question, attempt.provider.provider.id)
        routerUsed = true
        modelsUsed[ModelTier.ROUTER] = attempt.provider.model(ModelTier.ROUTER)
        val verdict = classifyWithFailover(input, attempt, budget, state, conversation, ::use)
        if (verdict.deep && tier == ModelTier.CHAT) tier = ModelTier.DEEP
        resolve(verdict, input)
      }
    }
    if (hierarchical) {
      open(conversation, groups)
      groups = activeGroups(conversation, input)
    }
    conversation.lastGroups = groups
    // Il tool "modello_avanzato" si offre solo finche' c'e' qualcosa da guadagnarci: si sta ancora
    // lavorando col modello della chat, e il provider ne ha davvero uno piu' capace.
    fun deepOffered(): Boolean = tier == ModelTier.CHAT && attempt.provider.model(ModelTier.DEEP) != attempt.provider.model(ModelTier.CHAT)
    var tools: List<ToolSpec> = specsFor(groups, input, deepOffered())
    var moreToolsUsed = 0
    var opens = 0

    val system = Message.System(input.systemPrompt)
    val messages = mutableListOf<Message>()
    messages += system
    messages += fit(HistoryCompactor.compact(conversation, budgetTokens = historyBudget(attempt.provider.provider.id)), capabilities(attempt.provider, tier), input.language)
    // Dove finisce la storia e comincia questa domanda: [trimForContext] riscrive solo la prima parte.
    var historyEnd = messages.size
    messages += Message.User(userTurn.parts)

    /**
     * La richiesta non ci stava (2.8.0, [FailoverDecision.TrimAndRetry]): la storia si ricompatta
     * a meta' del budget del provider di adesso, senza il traffico tool della domanda prima e senza
     * i suoi allegati, e i risultati dei tool di questa domanda (e gli allegati tradotti in testo)
     * scendono a un terzo del loro tetto. La domanda dell'utente e le coppie chiamata/risultato
     * restano intere: un risultato orfano e' un 400.
     */
    fun trimForContext() {
      val shorter = HistoryCompactor.compact(
        conversation,
        budgetTokens = historyBudget(attempt.provider.provider.id) / 2,
        includeToolRound = false,
        includeAttachments = false,
      )
      val limit = (config.toolTextChars / 3).coerceAtLeast(TRIMMED_TOOL_TEXT_MIN)
      val tail = messages.drop(historyEnd).mapIndexed { index, message ->
        when {
          message is Message.ToolResult -> message.copy(content = ToolText.limit(message.content, limit))
          index > 0 && message is Message.User && !message.hasBinaryParts -> Message.User(ToolText.limit(message.text, limit))
          else -> message
        }
      }
      messages.clear()
      messages += system
      messages += shorter
      historyEnd = messages.size
      messages += tail
    }

    val toolRound = mutableListOf<Message>()
    var answer: String? = null
    var answerProvider = attempt.provider.provider.id
    var steps = 0
    var toolChars = 0

    for (step in 1..config.maxRounds) {
      steps = step
      val forceFinal = step == config.maxRounds || budget.forceFinal
      val model = attempt.provider.model(tier)
      val reasoning = reasoningFor(input.settings.thinking, final = forceFinal)
      val request = ChatRequest(
        model = model,
        messages = if (forceFinal) messages + Message.System(input.forceFinalPrompt) else messages,
        tools = if (forceFinal) emptyList() else tools,
        toolChoice = if (forceFinal) ToolChoice.None else ToolChoice.Auto,
        reasoning = reasoning,
        maxOutputTokens = config.maxOutputTokens,
        temperature = config.temperature,
        readTimeoutMillis = config.readTimeoutFor(tier, reasoning, budget.remainingMillis),
      )
      state.value = AssistantState.Working(question, step, config.maxRounds, "thinking", 0, attempt.provider.provider.id, tier)
      val outcome = runTurnWithFailover(input, attempt, budget, state, request, messages, tier, forceFinal, ::trimForContext) { s ->
        waitedSeconds += s
      }
      // Chi ha risposto davvero: dopo un cambio di provider puo' essere un altro modello, e anche
      // un altro livello (la riserva riparte dalla chat: [tierAfterSwitch]).
      if (outcome.tier != tier) {
        tier = outcome.tier
        neutralize(messages)
      }
      modelsUsed[tier] = outcome.model
      use(attempt.provider.provider.id, tier, outcome.model)
      if (tier.ordinal > tierReached.ordinal) tierReached = tier
      outcome.usage?.let { usageTotal = usageTotal?.plus(it) ?: it }
      lastRateLimit = outcome.rateLimit
      diagnostics.rateLimit(attempt.provider.provider.id, outcome.rateLimit)
      answerProvider = attempt.provider.provider.id
      // Al giro forzato non ci sono tool: una chiamata che arriva lo stesso si ignora e vale il testo.
      // Una risposta vuota qui non arriva mai (2.8.0): [runTurnWithFailover] la riprova, cambia
      // provider o fallisce. Il controllo resta come argine: una bolla vuota non e' un successo.
      if (outcome.calls.isEmpty() || forceFinal) {
        answer = outcome.text?.trim().orEmpty()
        if (answer.isBlank()) {
          throw AssistantFailure(FailureKind.PROVIDER, null, provider = attempt.provider.provider.id, reason = SwitchReason.EMPTY_ANSWER, switches = attempt.switches.toList())
        }
        break
      }
      val assistant = Message.Assistant(outcome.text, outcome.calls, raw = outcome.raw, rawProvider = attempt.provider.provider.id)
      messages += assistant
      toolRound += assistant
      state.value = AssistantState.Working(question, step, config.maxRounds, statusKeyFor(outcome.calls), outcome.calls.size - 1, attempt.provider.provider.id, tier)

      // Le aperture di categorie e sottocategorie si decidono prima di eseguire: il testo che
      // torna al modello deve dire la verita' su cosa avra' al giro dopo.
      val opened = mutableMapOf<String, ToolOutput>()
      outcome.calls.forEach { call ->
        when (call.name) {
          ToolRegistry.MORE_TOOLS -> {
            val group = registry.group(call.arguments["gruppo"].string())
            opened[call.id] = when {
              group == null -> ToolOutput.error("gruppo sconosciuto")
              group !in groups && (group != registry.actionGroup || input.actionsEnabled) && moreToolsUsed < config.maxMoreTools -> {
                groups = groups + group
                moreToolsUsed++
                if (hierarchical) open(conversation, listOf(group))
                ToolOutput("ok: gli strumenti del gruppo ${group.id} saranno disponibili dal prossimo passo")
              }
              group in groups -> ToolOutput("ok: gli strumenti del gruppo ${group.id} sono gia' disponibili")
              else -> ToolOutput.error("non si possono aprire altri gruppi in questa domanda: rispondi con quello che hai")
            }
          }
          ToolRegistry.OPEN_CATEGORY -> {
            val category = registry.category(call.arguments["categoria"].string())
            opened[call.id] = when {
              category == null -> ToolOutput.error("categoria sconosciuta; quelle che esistono: ${registry.categories.joinToString(", ") { it.id }}")
              opens >= config.maxOpens -> ToolOutput.error("non si possono aprire altre categorie in questa domanda: rispondi con quello che hai")
              else -> {
                opens++
                val initial = registry.initialGroupsOf(category).filter { input.actionsEnabled || it != registry.actionGroup }
                open(conversation, initial, category)
                ToolOutput(
                  "ok: gli strumenti della categoria ${category.id} saranno disponibili dal prossimo passo" +
                    " (sottocategorie: ${registry.topGroupsOf(category).joinToString(", ") { it.id }})",
                )
              }
            }
          }
          ToolRegistry.OPEN_GROUP -> {
            val group = registry.group(call.arguments["sottocategoria"].string())
            opened[call.id] = when {
              group == null -> ToolOutput.error("sottocategoria sconosciuta")
              group == registry.actionGroup && !input.actionsEnabled -> ToolOutput.error("le azioni nell'app sono disattivate dall'utente")
              group in groups -> ToolOutput("ok: gli strumenti di ${group.id} sono gia' disponibili")
              opens >= config.maxOpens -> ToolOutput.error("non si possono aprire altre sottocategorie in questa domanda: rispondi con quello che hai")
              else -> {
                opens++
                open(conversation, listOf(group))
                ToolOutput("ok: gli strumenti di ${group.id} saranno disponibili dal prossimo passo")
              }
            }
          }
        }
      }

      val runs = executeParallel(outcome.calls, input.toolContext, budget, toolTraces, opened)
      val parts = mutableListOf<ContentPart>()
      runs.forEach { run ->
        val message = Message.ToolResult(run.call.id, run.call.name, run.output.text)
        messages += message
        toolRound += message
        toolChars += run.output.text.length
        parts += run.output.parts
        if (hierarchical) registry.find(run.call.name)?.let { conversation.touch(listOf(it.group)) }
      }

      // Un allegato: va al modello come parte di un messaggio se il livello profondo lo regge,
      // altrimenti l'app lo traduce in testo. In entrambi i casi la storia lo ricorda a parole.
      if (parts.isNotEmpty()) {
        val capabilities = attempt.provider.capabilities(attempt.provider.model(ModelTier.DEEP))
        val accepted = parts.filter { capabilities.accepts(it) }
        val rejected = parts.filter { !capabilities.accepts(it) }
        val name = parts.filterIsInstance<ContentPart.Document>().firstOrNull()?.name
        if (accepted.isNotEmpty()) {
          if (tier != ModelTier.DEEP) {
            tier = ModelTier.DEEP
            neutralize(messages)
          }
          val intro = AiPrompts.attachmentIntro(input.language, name)
          messages += Message.User(listOf(ContentPart.Text(intro)) + accepted)
          toolRound += Message.User("$intro (contenuto gia' letto)")
        }
        rejected.forEach { part ->
          val translated = input.attachmentFallback(part)?.takeIf { it.isNotBlank() }
          val content = if (translated != null) {
            AiPrompts.attachmentIntro(input.language, (part as? ContentPart.Document)?.name ?: name) + "\n" + ToolText.limit(translated, config.attachmentTextChars)
          } else {
            AiPrompts.attachmentUnreadable(input.language, (part as? ContentPart.Document)?.name ?: name)
          }
          val user = Message.User(content)
          messages += user
          toolRound += user
          toolChars += content.length
        }
      }

      // Molto testo dai tool: da qui in poi conviene il modello col contesto grande, se ce n'e' uno.
      if (tier == ModelTier.CHAT && toolChars > config.escalationChars && attempt.provider.model(ModelTier.DEEP) != attempt.provider.chatModel) {
        tier = ModelTier.DEEP
        neutralize(messages)
      }

      // L'ha chiesto il modello (o un tool che sa di aver portato roba pesante): il resto del giro
      // lo fa quello piu' capace. Cambiare modello a meta' e' come cambiare provider, quindi i
      // pezzi grezzi scritti dall'altro modello non possono restare nella storia.
      if (runs.any { it.output.escalate } && deepOffered()) {
        tier = ModelTier.DEEP
        neutralize(messages)
      }

      if (hierarchical) groups = activeGroups(conversation, input)
      tools = specsFor(groups, input, deepOffered())
    }
    val finalAnswer = answer ?: throw AssistantFailure(FailureKind.TIMEOUT, null, provider = attempt.provider.provider.id, switches = attempt.switches.toList())
    val (cleanText, chips) = ChipParser.extract(finalAnswer, input.chipFilter)
    conversation.exchanges += Exchange(question, cleanText, chips, answerProvider, clock(), attachments = input.attachments)
    conversation.lastToolRound = toolRound.toList()
    conversation.lastActivityMillis = clock()
    conversation.provider = answerProvider
    val log = AiRequestLog(
      startedAtMillis = startedAt,
      question = question,
      mode = input.mode,
      provider = input.providers.first().provider.id,
      model = attempt.provider.chatModel,
      switchedTo = attempt.switched.toList(),
      groups = groups.map { it.id },
      classifierUsed = routerUsed,
      tools = toolTraces.toList(),
      steps = steps,
      usage = usageTotal,
      durationMillis = clock() - startedAt,
      outcome = "ok",
      error = null,
      rateLimit = lastRateLimit,
      waitedSeconds = waitedSeconds,
      tierReached = tierReached,
      models = modelsUsed.toMap(),
      modelsUsed = used.toList(),
      switches = attempt.switches.toList(),
    )
    diagnostics.add(log)
    return AskResult(cleanText, chips, answerProvider, usageTotal, toolTraces.map { it.name }.distinct(), log, tierReached)
  }

  /**
   * Il messaggio dell'utente con i suoi allegati: entrano come parti se il livello con cui si parte
   * li regge; se li regge solo il profondo, si parte dal profondo; se non li regge nessuno, l'app
   * li traduce in testo (o il modello viene avvertito che non li puo' leggere).
   */
  private suspend fun prepareUserTurn(input: AskInput<C>, ready: ReadyProvider, startTier: ModelTier): UserTurn {
    val attachments = input.attachments
    if (attachments.isEmpty()) return UserTurn(listOf(ContentPart.Text(input.question)), startTier)
    var tier = startTier
    val chatCapabilities = ready.capabilities(ready.model(ModelTier.CHAT))
    val deepCapabilities = ready.capabilities(ready.model(ModelTier.DEEP))
    val deepIsDifferent = ready.model(ModelTier.DEEP) != ready.model(ModelTier.CHAT)
    if (tier == ModelTier.CHAT && deepIsDifferent && attachments.any { !chatCapabilities.accepts(it) && deepCapabilities.accepts(it) }) {
      tier = ModelTier.DEEP
    }
    val capabilities = if (tier == ModelTier.DEEP) deepCapabilities else chatCapabilities
    val parts = mutableListOf<ContentPart>()
    val notes = StringBuilder()
    attachments.forEach { part ->
      if (capabilities.accepts(part)) {
        parts += part
      } else {
        val translated = input.attachmentFallback(part)?.takeIf { it.isNotBlank() }
        notes.append("\n\n").append(
          if (translated != null) {
            AiPrompts.userAttachmentText(input.language, part.displayName) + "\n" + ToolText.limit(translated, config.attachmentTextChars)
          } else {
            AiPrompts.attachmentUnreadable(input.language, part.displayName)
          },
        )
      }
    }
    return UserTurn(listOf(ContentPart.Text(input.question + notes)) + parts, tier)
  }

  private fun capabilities(ready: ReadyProvider, tier: ModelTier): ModelCapabilities = ready.capabilities(ready.model(tier))

  /**
   * La storia riletta per il modello di adesso: le parti che non regge (lo screenshot di prima,
   * dopo un cambio di provider) diventano una riga che dice che c'erano. Meglio di un 400.
   */
  private fun fit(messages: List<Message>, capabilities: ModelCapabilities, language: String): List<Message> = messages.map { message ->
    if (message !is Message.User || !message.hasBinaryParts || message.parts.all { capabilities.accepts(it) }) return@map message
    val kept = message.parts.filter { capabilities.accepts(it) }
    val dropped = message.parts.filter { !capabilities.accepts(it) }
    Message.User(kept + ContentPart.Text(dropped.joinToString("\n") { AiPrompts.attachmentDropped(language, it.displayName) }))
  }

  /** Cosa lo stadio 1 ha aperto davvero: i gruppi detti, o quelli di partenza della categoria. */
  private fun resolve(verdict: RouterVerdict, input: AskInput<C>): Set<AiToolGroup> {
    if (!registry.hierarchical) return verdict.groups
    val category = verdict.category
    return when {
      verdict.groups.isNotEmpty() -> {
        category?.let { input.conversation.loadedCategories += it }
        verdict.groups
      }
      category != null -> registry.initialGroupsOf(category).filter { input.actionsEnabled || it != registry.actionGroup }.toSet()
      else -> emptySet()
    }
  }

  /** Apre dei gruppi nella conversazione: da adesso restano, e la loro categoria con loro. */
  private fun open(conversation: Conversation, groups: Collection<AiToolGroup>, category: AiToolCategory? = null) {
    conversation.touch(groups)
    groups.forEach { group -> group.categoryOrNull()?.let { conversation.loadedCategories += it } }
    category?.let { conversation.loadedCategories += it }
  }

  private fun AiToolGroup.categoryOrNull(): AiToolCategory? = category ?: parent?.categoryOrNull()

  /**
   * I gruppi aperti che entrano nel giro: tutti, dal piu' recente, finche' i loro tool stanno nel
   * tetto; gli altri escono dalla conversazione (e si riaprono con una chiamata, se servono).
   */
  private fun activeGroups(conversation: Conversation, input: AskInput<C>): Set<AiToolGroup> {
    val visible = conversation.loadedGroups.filter { input.actionsEnabled || it != registry.actionGroup }
    val kept = mutableListOf<AiToolGroup>()
    var count = 0
    for (group in visible.asReversed()) {
      val size = registry.toolCount(setOf(group))
      if (kept.isNotEmpty() && count + size > config.maxLoadedTools) break
      kept += group
      count += size
    }
    conversation.loadedGroups.removeAll((visible - kept.toSet()).toSet())
    return kept.toSet()
  }

  private fun allGroups(input: AskInput<C>): Set<AiToolGroup> = registry.visibleGroups(input.actionsEnabled).toSet()

  private fun specsFor(groups: Set<AiToolGroup>, input: AskInput<C>, deep: Boolean = false): List<ToolSpec> {
    val visible = groups.filter { it != registry.actionGroup || input.actionsEnabled }.toSet()
    val specs = registry.specsFor(visible)
    if (registry.hierarchical) {
      val loadedCategories = input.conversation.loadedCategories.toSet()
      val categoriesMissing = registry.categories.any { it !in loadedCategories }
      return specs + listOfNotNull(
        registry.openCategoryTool.takeIf { categoriesMissing },
        registry.openGroupTool(loadedCategories, visible, input.actionsEnabled),
        registry.deepTool.takeIf { deep },
      )
    }
    val missing = registry.visibleGroups(input.actionsEnabled).any { it !in visible }
    return specs + listOfNotNull(
      registry.moreTools.takeIf { missing },
      registry.deepTool.takeIf { deep },
    )
  }

  private fun historyBudget(provider: ProviderId): Int = if (provider == ProviderId.GROQ) config.historyBudgetGroq else config.historyBudgetOther

  private fun reasoningFor(level: ThinkingLevel, final: Boolean): ReasoningLevel = when (level) {
    ThinkingLevel.LOW -> if (final) ReasoningLevel.NONE else ReasoningLevel.LOW
    ThinkingLevel.MEDIUM -> if (final) ReasoningLevel.NONE else ReasoningLevel.MEDIUM
    ThinkingLevel.HIGH -> if (final) ReasoningLevel.LOW else ReasoningLevel.HIGH
  }

  private fun statusKeyFor(calls: List<ToolCall>): String {
    val first = calls.firstOrNull() ?: return "thinking"
    if (first.name == ToolRegistry.DEEP_MODEL) return "deep_model"
    if (first.name == ToolRegistry.MORE_TOOLS || first.name == ToolRegistry.OPEN_CATEGORY || first.name == ToolRegistry.OPEN_GROUP) return "more_tools"
    return registry.find(first.name)?.group?.statusKey ?: "thinking"
  }

  /** Le parti grezze appartengono al modello che le ha scritte: cambiando modello si buttano. */
  private fun neutralize(messages: MutableList<Message>) {
    for (index in messages.indices) {
      val message = messages[index]
      if (message is Message.Assistant && message.raw != null) messages[index] = message.copy(raw = null, rawProvider = null)
    }
  }

  private fun usageEvent(
    input: AskInput<C>,
    ready: ReadyProvider,
    model: String,
    tier: ModelTier,
    startedAt: Long,
    usage: Usage?,
    rateLimit: RateLimitInfo,
    error: AiError? = null,
  ) {
    val sink = usageSink ?: return
    sink.onTurn(
      AiUsageEvent(
        atMillis = startedAt,
        conversationId = input.conversation.id,
        provider = ready.provider.id,
        model = model,
        tier = tier,
        usage = usage,
        rateLimit = rateLimit,
        durationMillis = clock() - startedAt,
        error = error,
      ),
    )
  }

  private suspend fun classifyWithFailover(
    input: AskInput<C>,
    attempt: Attempt,
    budget: TimeBudget,
    state: MutableStateFlow<AssistantState>,
    conversation: Conversation,
    /** Dove segnare il modello del router quando ha risposto davvero. */
    used: (ProviderId, ModelTier, String) -> Unit,
  ): RouterVerdict {
    while (true) {
      val ready = attempt.provider
      val model = ready.model(ModelTier.ROUTER)
      val started = clock()
      try {
        val (verdict, turn) = router.classifyWithTurn(
          provider = ready.provider,
          model = model,
          question = input.question,
          previousQuestion = conversation.exchanges.lastOrNull()?.question,
          previousGroups = conversation.lastGroups,
          language = input.language,
          actionsEnabled = input.actionsEnabled,
          hint = input.routerHint,
          loadedCategories = conversation.loadedCategories.toSet(),
        )
        usageEvent(input, ready, model, ModelTier.ROUTER, started, turn.usage, turn.rateLimit)
        used(ready.provider.id, ModelTier.ROUTER, model)
        return verdict
      } catch (e: CancellationException) {
        throw e
      } catch (e: AiError.Unauthorized) {
        usageEvent(input, ready, model, ModelTier.ROUTER, started, null, RateLimitInfo.EMPTY, e)
        throw AssistantFailure(FailureKind.UNAUTHORIZED, e, provider = ready.provider.id, switches = attempt.switches.toList())
      } catch (e: AiError.BadRequest) {
        usageEvent(input, ready, model, ModelTier.ROUTER, started, null, RateLimitInfo.EMPTY, e)
        // Un 400 del router riguarda il suo modello, non la chat dello stesso provider: nessun
        // cambio di provider per lui (2.8.0), il ripiego del router c'e' gia'. Un modello sparito
        // pero' si dice all'app, che lo toglie dalle scelte.
        if (e.reason == BadReason.MODEL_UNAVAILABLE) input.onModelUnavailable(ready.provider.id, model)
        return RouterVerdict(router.fallback(conversation.lastGroups + input.routerHint))
      } catch (e: Throwable) {
        usageEvent(input, ready, model, ModelTier.ROUTER, started, null, (e as? AiError.RateLimited)?.rateLimit ?: RateLimitInfo.EMPTY, e as? AiError)
        // Lo stadio 1 non fa fallire la domanda: si prova il prossimo provider (mai col servizio
        // fissato: un'attesa o un fallimento qui valgono come "nessun verdetto"), poi il ripiego.
        val decision = failover.decide(e, ready.provider.id, remaining(input, attempt), attempt.waits, attempt.retries, budget.remainingMillis, pinned = input.pinProvider)
        when (decision) {
          is FailoverDecision.Switch -> {
            switchTo(input, attempt, decision.to, state, SwitchReason.of(e) ?: SwitchReason.SERVER)
            if (attempt.provider.provider.id == ProviderId.OPENROUTER) {
              return if (registry.hierarchical) RouterVerdict(input.routerHint) else RouterVerdict(allGroups(input))
            }
          }
          is FailoverDecision.RetrySame -> attempt.retries++
          else -> return RouterVerdict(router.fallback(conversation.lastGroups + input.routerHint))
        }
      }
    }
  }

  private fun remaining(input: AskInput<C>, attempt: Attempt): List<ProviderId> {
    val order = input.providers.map { it.provider.id }
    val index = order.indexOf(attempt.provider.provider.id)
    return if (index < 0) order else order.drop(index + 1)
  }

  private fun switchTo(input: AskInput<C>, attempt: Attempt, to: ProviderId, state: MutableStateFlow<AssistantState>, reason: SwitchReason) {
    val next = input.providers.first { it.provider.id == to }
    val from = attempt.provider.provider.id
    state.value = AssistantState.SwitchingProvider(input.question, from, to, reason)
    attempt.switched += to
    attempt.switches += ProviderSwitch(from, to, reason)
    attempt.provider = next
    attempt.retries = 0
    attempt.trims = 0
    attempt.emptyRetries = 0
  }

  /**
   * Un giro con il suo failover: gli errori passano da [FailoverPolicy.decide], e dalla 2.8.0 anche
   * una risposta finale vuota ([FailoverPolicy.decideEmpty]) — un giro che chiude senza tool call
   * e senza una parola non torna mai come risposta. [messages] e' la conversazione della domanda,
   * che [trim] puo' riscrivere al suo posto quando la richiesta non ci sta; [forceFinal] dice se e'
   * il giro senza strumenti, dove una tool call non vale come risposta.
   */
  private suspend fun runTurnWithFailover(
    input: AskInput<C>,
    attempt: Attempt,
    budget: TimeBudget,
    state: MutableStateFlow<AssistantState>,
    request: ChatRequest,
    messages: List<Message>,
    startTier: ModelTier,
    forceFinal: Boolean,
    trim: () -> Unit,
    onWaited: (Int) -> Unit,
  ): TurnOutcome {
    var current = request
    var tier = startTier
    // Quanti messaggi di [current] vengono da [messages]: il resto (il prompt del giro forzato) si
    // riattacca in coda a ogni riscrittura.
    var baseSize = messages.size

    /**
     * [messages] piu' [extra] per il provider e il livello di adesso: le parti grezze scritte da un
     * altro provider si buttano, gli allegati che il modello non regge diventano una riga.
     */
    fun rewritten(extra: List<Message>): List<Message> {
      val own = attempt.provider.provider.id
      val capabilities = capabilities(attempt.provider, tier)
      val neutral = messages.map { if (it is Message.Assistant && it.rawProvider != null && it.rawProvider != own) it.copy(raw = null, rawProvider = null) else it }
      return fit(neutral, capabilities, input.language) + fit(extra, capabilities, input.language)
    }

    fun switchProvider(to: ProviderId, reason: SwitchReason) {
      val extra = current.messages.drop(baseSize)
      switchTo(input, attempt, to, state, reason)
      tier = tierAfterSwitch(input, attempt.provider, tier, messages)
      // La stessa conversazione, riscritta per il nuovo provider. Tetto di uscita e ragionamento
      // tornano quelli del giro: una riprova dopo una risposta vuota li aveva adattati al modello
      // di prima, non a questo.
      val onOpenRouter = attempt.provider.provider.id == ProviderId.OPENROUTER
      current = current.copy(
        model = attempt.provider.model(tier),
        messages = rewritten(extra),
        tools = if (onOpenRouter && !registry.hierarchical && current.tools.isNotEmpty()) specsFor(allGroups(input), input) else current.tools,
        maxOutputTokens = request.maxOutputTokens,
        reasoning = request.reasoning,
        readTimeoutMillis = config.readTimeoutFor(tier, request.reasoning, budget.remainingMillis),
      )
    }

    while (true) {
      val started = clock()
      val outcome: TurnOutcome? = try {
        runTurn(attempt.provider, current, input, state, tier, started)
      } catch (e: CancellationException) {
        throw e
      } catch (e: AssistantFailure) {
        throw e
      } catch (e: Throwable) {
        usageEvent(input, attempt.provider, current.model, tier, started, null, (e as? AiError.RateLimited)?.rateLimit ?: RateLimitInfo.EMPTY, e as? AiError)
        if (e is AiError.BadRequest && e.reason == BadReason.MODEL_UNAVAILABLE) input.onModelUnavailable(attempt.provider.provider.id, current.model)
        val decision = failover.decide(
          e,
          attempt.provider.provider.id,
          remaining(input, attempt),
          attempt.waits,
          attempt.retries,
          budget.remainingMillis,
          pinned = input.pinProvider,
          trimsDone = attempt.trims,
        )
        when (decision) {
          is FailoverDecision.Wait -> {
            attempt.waits++
            onWaited(decision.seconds)
            var left = decision.seconds
            while (left > 0) {
              state.value = AssistantState.WaitingRateLimit(input.question, attempt.provider.provider.id, left)
              delay(1_000)
              left--
            }
          }
          is FailoverDecision.Switch -> switchProvider(decision.to, SwitchReason.of(e) ?: SwitchReason.BAD_REQUEST)
          FailoverDecision.RetrySame -> {
            attempt.retries++
            delay(1_500)
          }
          FailoverDecision.TrimAndRetry -> {
            attempt.trims++
            val extra = current.messages.drop(baseSize)
            trim()
            baseSize = messages.size
            current = current.copy(messages = rewritten(extra))
          }
          is FailoverDecision.Fail -> throw AssistantFailure(
            decision.kind,
            e as? AiError,
            decision.retryAfterSec,
            provider = attempt.provider.provider.id,
            reason = SwitchReason.of(e),
            switches = attempt.switches.toList(),
          )
        }
        null
      }
      if (outcome == null) continue

      // Una risposta finale vuota non e' mai un successo (2.8.0): prima diventava una bolla vuota
      // salvata come DONE, tranne sul giro forzato.
      val final = outcome.calls.isEmpty() || forceFinal
      if (!final || !outcome.text.isNullOrBlank()) return outcome
      val decision = failover.decideEmpty(outcome.finish, attempt.provider.provider.id, remaining(input, attempt), attempt.emptyRetries, budget.remainingMillis, pinned = input.pinProvider)
      when (decision) {
        FailoverDecision.RetrySame -> {
          attempt.emptyRetries++
          current = retryAfterEmpty(current, outcome.finish, attempt.provider)
        }
        is FailoverDecision.Switch -> switchProvider(decision.to, SwitchReason.EMPTY_ANSWER)
        is FailoverDecision.Fail -> {
          // Sul giro forzato dal budget il vuoto e' il tempo che e' finito, come prima della 2.8.0.
          val kind = if (decision.kind == FailureKind.PROVIDER && forceFinal && budget.forceFinal) FailureKind.TIMEOUT else decision.kind
          throw AssistantFailure(
            kind,
            null,
            provider = attempt.provider.provider.id,
            reason = if (kind == FailureKind.BLOCKED) null else SwitchReason.EMPTY_ANSWER,
            switches = attempt.switches.toList(),
          )
        }
        is FailoverDecision.Wait, FailoverDecision.TrimAndRetry -> error("decideEmpty non aspetta e non accorcia")
      }
    }
  }

  /**
   * La riprova di una risposta vuota (2.8.0). Con la fine `LENGTH` il modello ha speso tutti i token
   * a pensare (su Gemini il thinking conta nel tetto di uscita): si triplica il tetto, fin dove il
   * catalogo dice che il modello arriva; se non si puo' salire, si pensa meno. Con `OTHER` (la
   * chiamata malformata di Gemini) o `STOP` la stessa richiesta: e' un'estrazione andata male.
   */
  private fun retryAfterEmpty(request: ChatRequest, finish: FinishReason, ready: ReadyProvider): ChatRequest {
    if (finish != FinishReason.LENGTH) return request
    val tokens = request.maxOutputTokens ?: config.maxOutputTokens
    val ceiling = ready.catalogue?.chat(request.model)?.maxOutputTokens
    val raised = (tokens * 3).let { if (ceiling != null) minOf(it, ceiling) else it }
    return when {
      raised > tokens -> request.copy(maxOutputTokens = raised)
      request.reasoning != ReasoningLevel.NONE -> request.copy(reasoning = if (request.reasoning == ReasoningLevel.LOW) ReasoningLevel.NONE else ReasoningLevel.LOW)
      else -> request
    }
  }

  /**
   * Il livello con cui la riserva riparte (1.29.0). Il profondo era una scelta sui modelli del
   * provider di prima — il router che lo giudicava necessario, i risultati lunghi, il tool
   * `modello_avanzato` — e sulla riserva il profondo e' il modello piu' grosso, quello che si trova
   * al limite per primo: ereditarlo alla cieca mandava ogni domanda complessa sul profondo di
   * OpenRouter, qualunque cosa avesse scelto l'utente. Quindi si riparte dalla chat, e le
   * escalation si rivalutano li' (i risultati lunghi e il tool si ripresentano da soli), tranne
   * quando il profondo serve per forza: l'app l'ha chiesto ([AskInput.deepRequested]), o nella
   * storia ci sono allegati che sulla riserva legge solo lui. Dalla chat non si scende comunque.
   */
  private fun tierAfterSwitch(input: AskInput<C>, next: ReadyProvider, tier: ModelTier, messages: List<Message>): ModelTier {
    if (tier != ModelTier.DEEP) return tier
    if (input.deepRequested) return ModelTier.DEEP
    val chat = next.capabilities(next.model(ModelTier.CHAT))
    val deep = next.capabilities(next.model(ModelTier.DEEP))
    val onlyDeepReads = messages.any { it is Message.User && it.hasBinaryParts && it.parts.any { part -> !chat.accepts(part) && deep.accepts(part) } }
    return if (onlyDeepReads) ModelTier.DEEP else ModelTier.CHAT
  }

  /**
   * Un giro in stream: testo (mostrato dopo 24 caratteri o 150 ms senza tool call), tool call, fine.
   * Se il flusso si spezza a meta' — la rete cade, il server chiude, un pezzo non si legge — dopo
   * aver gia' portato qualcosa, si rifa' lo stesso giro senza stream, una volta: costa una richiesta
   * e risparmia i minuti gia' spesi nei tool. Un rifiuto (chiave, 400) non si riprova.
   */
  private suspend fun runTurn(
    ready: ReadyProvider,
    request: ChatRequest,
    input: AskInput<C>,
    state: MutableStateFlow<AssistantState>,
    tier: ModelTier,
    startedAt: Long,
  ): TurnOutcome {
    val assembler = ToolCallAssembler()
    val text = StringBuilder()
    var firstTextAt = 0L
    var published = false
    var raw: JsonElement? = null
    var finish: ChatDelta.Finish? = null
    try {
      ready.provider.stream(request).collect { delta ->
        when (delta) {
          is ChatDelta.Text -> {
            if (text.isEmpty()) firstTextAt = clock()
            text.append(delta.text)
            if (assembler.isEmpty && (text.length >= config.publishMinChars || clock() - firstTextAt >= config.publishMinMillis)) {
              published = true
              state.value = AssistantState.Answering(input.question, text.toString(), ready.provider.id, tier)
            }
          }
          is ChatDelta.ToolCallPart -> {
            assembler.add(delta)
            if (published) {
              // Il testo era un preambolo ("controllo..."): torna il lavoro, non la risposta.
              published = false
              state.value = AssistantState.Working(input.question, 0, config.maxRounds, "thinking", 0, ready.provider.id, tier)
            }
          }
          is ChatDelta.Raw -> raw = delta.raw
          is ChatDelta.Finish -> finish = delta
        }
      }
    } catch (e: CancellationException) {
      throw e
    } catch (e: AiError) {
      val brokenMidway = (text.isNotEmpty() || !assembler.isEmpty) &&
        (e is AiError.Network || e is AiError.Timeout || e is AiError.Server || e is AiError.Parse)
      if (!brokenMidway) throw e
      usageEvent(input, ready, request.model, tier, startedAt, null, RateLimitInfo.EMPTY, e)
      state.value = AssistantState.Working(input.question, 0, config.maxRounds, "thinking", 0, ready.provider.id, tier)
      val retryStarted = clock()
      val turn = ready.provider.complete(request)
      usageEvent(input, ready, request.model, tier, retryStarted, turn.usage, turn.rateLimit)
      val message = turn.message
      val fullText = message.text?.takeIf { it.isNotBlank() }
      if (message.toolCalls.isEmpty() && fullText != null) {
        state.value = AssistantState.Answering(input.question, fullText, ready.provider.id, tier)
      }
      return TurnOutcome(fullText, message.toolCalls, message.raw, turn.usage, turn.rateLimit, turn.finishReason, tier, request.model)
    }
    val calls = assembler.build()
    if (calls.isEmpty() && text.isNotEmpty() && !published) {
      state.value = AssistantState.Answering(input.question, text.toString(), ready.provider.id, tier)
    }
    usageEvent(input, ready, request.model, tier, startedAt, finish?.usage, finish?.rateLimit ?: RateLimitInfo.EMPTY)
    return TurnOutcome(
      text = text.toString().takeIf { it.isNotBlank() },
      calls = calls,
      raw = raw,
      usage = finish?.usage,
      rateLimit = finish?.rateLimit ?: RateLimitInfo.EMPTY,
      finish = finish?.reason ?: if (calls.isNotEmpty()) FinishReason.TOOL_CALLS else FinishReason.STOP,
      tier = tier,
      model = request.model,
    )
  }

  private suspend fun executeParallel(
    calls: List<ToolCall>,
    ctx: C,
    budget: TimeBudget,
    traces: MutableList<ToolTrace>,
    /** Le risposte gia' decise per le chiamate built-in (aperture di gruppi e categorie). */
    decided: Map<String, ToolOutput>,
  ): List<ToolRun> = coroutineScope {
    val semaphore = Semaphore(config.parallelTools)
    val outcomes = calls.map { call ->
      async(Dispatchers.IO) {
        semaphore.withPermit {
          val started = clock()
          val output = decided[call.id] ?: if (call.name == ToolRegistry.DEEP_MODEL) {
            ToolOutput("ok: dal prossimo passo rispondi tu, con il modello piu' capace; continua da dove sei, non ricominciare", escalate = true)
          } else {
            val tool = registry.find(call.name)
            if (tool == null) {
              ToolOutput.error("strumento sconosciuto \"${call.name}\"")
            } else {
              val timeout = minOf(config.toolTimeoutMillis, (budget.remainingMillis - 5_000).coerceAtLeast(3_000))
              withTimeoutOrNull(timeout) {
                runCatching { tool.run(call.arguments, ctx) }.getOrElse { e ->
                  if (e is CancellationException) throw e
                  ToolOutput.error(e.message ?: e::class.simpleName ?: "errore")
                }
              } ?: ToolOutput.error("lo strumento non ha risposto in tempo")
            }
          }
          val limited = output.copy(text = ToolText.limit(output.text, config.toolTextChars))
          ToolRun(call, limited) to ToolTrace(call.name, clock() - started, !limited.text.startsWith("errore"), limited.text.length)
        }
      }
    }.awaitAll()
    outcomes.forEach { traces += it.second }
    outcomes.map { it.first }
  }
}

/** Sotto questo tetto un risultato di tool accorciato non dice piu' niente: e' il pavimento di [AiOrchestrator]'s trim. */
private const val TRIMMED_TOOL_TEXT_MIN = 400

/**
 * La fine di una domanda che non ha risposta: la sessione la traduce in [AssistantState.Failed].
 * Dalla 2.8.0 dice anche chi ha fallito per ultimo ([provider]), cosa gli e' successo ([reason]:
 * un `tool_use_failed`, un modello sparito, una risposta vuota) e i cambi di servizio fatti prima
 * ([switches]), per la frase della card e per il foglio "Dettagli".
 */
class AssistantFailure(
  val kind: FailureKind,
  val error: AiError?,
  val retryAfterSec: Int? = null,
  val provider: ProviderId? = null,
  val reason: SwitchReason? = null,
  val switches: List<ProviderSwitch> = emptyList(),
) : Exception(error?.message ?: kind.name)

/**
 * I marcatori `[[id]]` e `[[id:valore]]` in fondo alla risposta diventano chip; il testo mostrato
 * non li ha. Quali id valgono lo decide l'app con [accept]; i chip ammessi sono al massimo tre.
 */
object ChipParser {
  private val marker = Regex("\\[\\[([^\\]]+)]]")

  fun extract(text: String, accept: (AnswerChip) -> Boolean = { true }, max: Int = 3): Pair<String, List<AnswerChip>> {
    val chips = mutableListOf<AnswerChip>()
    marker.findAll(text).forEach { match ->
      val body = match.groupValues[1].trim()
      val id = body.substringBefore(':').trim().lowercase()
      val value = if (':' in body) body.substringAfter(':').trim().takeIf { it.isNotEmpty() } else null
      val chip = AnswerChip(id, value)
      if (id.isNotEmpty() && accept(chip) && chip !in chips && chips.size < max) chips += chip
    }
    val clean = marker.replace(text, "").replace(Regex("[ \\t]+\\n"), "\n").replace(Regex("\\n{3,}"), "\n\n").trim()
    return clean to chips
  }
}
