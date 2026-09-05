package dev.antigravity.fluidengine.ai.orchestrator

import dev.antigravity.fluidengine.ai.keys.AiSettings
import dev.antigravity.fluidengine.ai.keys.ThinkingLevel
import dev.antigravity.fluidengine.ai.net.AiError
import dev.antigravity.fluidengine.ai.net.RateLimitInfo
import dev.antigravity.fluidengine.ai.net.string
import dev.antigravity.fluidengine.ai.provider.ChatDelta
import dev.antigravity.fluidengine.ai.provider.ChatRequest
import dev.antigravity.fluidengine.ai.provider.ContentPart
import dev.antigravity.fluidengine.ai.provider.FinishReason
import dev.antigravity.fluidengine.ai.provider.Message
import dev.antigravity.fluidengine.ai.provider.ModelTier
import dev.antigravity.fluidengine.ai.provider.ProviderId
import dev.antigravity.fluidengine.ai.provider.ReadyProvider
import dev.antigravity.fluidengine.ai.provider.ReasoningLevel
import dev.antigravity.fluidengine.ai.provider.ToolCall
import dev.antigravity.fluidengine.ai.provider.ToolCallAssembler
import dev.antigravity.fluidengine.ai.provider.ToolChoice
import dev.antigravity.fluidengine.ai.provider.ToolSpec
import dev.antigravity.fluidengine.ai.provider.Usage
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
  /** Quanto testo di un allegato tradotto dall'app puo' entrare nel giro. */
  val attachmentTextChars: Int = 12_000,
  val publishMinChars: Int = 24,
  val publishMinMillis: Long = 150L,
  val temperature: Double = 0.3,
)

/**
 * Il cervello: stadio 1 (gruppi, o il pre-router dell'app) -> giro dei tool in stream con
 * esecuzione parallela, budget di tempo e di giri, cambio di provider sui 429 e sui 5xx, attesa
 * col conto alla rovescia quando non c'e' nessuno a cui passare. In piu', rispetto al primo
 * orchestratore: tre livelli di modello con l'escalation al profondo quando un tool porta un
 * allegato o i risultati si fanno lunghi, e la riprova senza stream quando il flusso si spezza a
 * meta'. Tutto lo stato osservabile passa da [ask]'s `state`.
 */
class AiOrchestrator<C>(
  private val registry: ToolRegistry<C>,
  private val router: AiRouter,
  private val diagnostics: AiDiagnosticsLog,
  private val failover: FailoverPolicy = FailoverPolicy(),
  private val config: AiOrchestratorConfig = AiOrchestratorConfig(),
  private val clock: () -> Long = System::currentTimeMillis,
) {

  private class Attempt(var provider: ReadyProvider, val switched: MutableList<ProviderId> = mutableListOf(), var waits: Int = 0, var retries: Int = 0)

  private class TurnOutcome(val text: String?, val calls: List<ToolCall>, val raw: JsonElement?, val usage: Usage?, val rateLimit: RateLimitInfo, val finish: FinishReason)

  private class ToolRun(val call: ToolCall, val output: ToolOutput)

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
    var tierReached = tier
    val modelsUsed = linkedMapOf<ModelTier, String>()

    // Stadio 1: i gruppi. Il pre-router dell'app, se ha deciso, vince; su OpenRouter il catalogo va intero.
    val preselected = input.preselectedGroups?.filter { input.actionsEnabled || it != registry.actionGroup }?.toSet()
    var groups: Set<AiToolGroup> = when {
      attempt.provider.provider.id == ProviderId.OPENROUTER -> allGroups(input)
      !preselected.isNullOrEmpty() -> preselected
      else -> {
        state.value = AssistantState.Classifying(question, attempt.provider.provider.id)
        routerUsed = true
        modelsUsed[ModelTier.ROUTER] = attempt.provider.model(ModelTier.ROUTER)
        val verdict = classifyWithFailover(input, attempt, budget, state, conversation)
        if (verdict.deep && tier == ModelTier.CHAT) tier = ModelTier.DEEP
        verdict.groups
      }
    }
    conversation.lastGroups = groups
    var tools: List<ToolSpec> = specsFor(groups, input)
    var moreToolsUsed = 0

    val messages = mutableListOf<Message>()
    messages += Message.System(input.systemPrompt)
    messages += HistoryCompactor.compact(conversation, budgetTokens = historyBudget(attempt.provider.provider.id))
    messages += Message.User(question)
    val toolRound = mutableListOf<Message>()
    var answer: String? = null
    var answerProvider = attempt.provider.provider.id
    var steps = 0
    var toolChars = 0

    for (step in 1..config.maxRounds) {
      steps = step
      val forceFinal = step == config.maxRounds || budget.forceFinal
      val model = attempt.provider.model(tier)
      modelsUsed[tier] = model
      if (tier.ordinal > tierReached.ordinal) tierReached = tier
      val request = ChatRequest(
        model = model,
        messages = if (forceFinal) messages + Message.System(input.forceFinalPrompt) else messages,
        tools = if (forceFinal) emptyList() else tools,
        toolChoice = if (forceFinal) ToolChoice.None else ToolChoice.Auto,
        reasoning = reasoningFor(input.settings.thinking, final = forceFinal),
        maxOutputTokens = config.maxOutputTokens,
        temperature = config.temperature,
      )
      state.value = AssistantState.Working(question, step, config.maxRounds, "thinking", 0, attempt.provider.provider.id, tier)
      val outcome = runTurnWithFailover(input, attempt, budget, state, request, messages, tier) { s ->
        waitedSeconds += s
      }
      outcome.usage?.let { usageTotal = usageTotal?.plus(it) ?: it }
      lastRateLimit = outcome.rateLimit
      diagnostics.rateLimit(attempt.provider.provider.id, outcome.rateLimit)
      answerProvider = attempt.provider.provider.id
      // Al giro forzato non ci sono tool: una chiamata che arriva lo stesso si ignora e vale il testo.
      if (outcome.calls.isEmpty() || forceFinal) {
        answer = outcome.text?.trim().orEmpty()
        if (answer.isBlank() && outcome.finish == FinishReason.BLOCKED) throw AssistantFailure(FailureKind.BLOCKED, null)
        if (answer.isBlank() && forceFinal) throw AssistantFailure(FailureKind.TIMEOUT, null)
        break
      }
      val assistant = Message.Assistant(outcome.text, outcome.calls, raw = outcome.raw, rawProvider = attempt.provider.provider.id)
      messages += assistant
      toolRound += assistant
      state.value = AssistantState.Working(question, step, config.maxRounds, statusKeyFor(outcome.calls), outcome.calls.size - 1, attempt.provider.provider.id, tier)
      val runs = executeParallel(outcome.calls, input.toolContext, budget, toolTraces)
      val parts = mutableListOf<ContentPart>()
      runs.forEach { run ->
        val message = Message.ToolResult(run.call.id, run.call.name, run.output.text)
        messages += message
        toolRound += message
        toolChars += run.output.text.length
        parts += run.output.parts
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

      // altri_tool: i gruppi chiesti entrano nel giro dopo, al massimo due volte per domanda.
      outcome.calls.filter { it.name == ToolRegistry.MORE_TOOLS }.forEach { call ->
        if (moreToolsUsed < config.maxMoreTools) {
          registry.group(call.arguments["gruppo"].string())?.let { group ->
            if (group !in groups && (group != registry.actionGroup || input.actionsEnabled)) {
              groups = groups + group
              tools = specsFor(groups, input)
              moreToolsUsed++
            }
          }
        }
      }
    }
    val finalAnswer = answer ?: throw AssistantFailure(FailureKind.TIMEOUT, null)
    val (cleanText, chips) = ChipParser.extract(finalAnswer, input.chipFilter)
    conversation.exchanges += Exchange(question, cleanText, chips, answerProvider, clock())
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
    )
    diagnostics.add(log)
    return AskResult(cleanText, chips, answerProvider, usageTotal, toolTraces.map { it.name }.distinct(), log, tierReached)
  }

  private fun allGroups(input: AskInput<C>): Set<AiToolGroup> = registry.visibleGroups(input.actionsEnabled).toSet()

  private fun specsFor(groups: Set<AiToolGroup>, input: AskInput<C>): List<ToolSpec> {
    val visible = groups.filter { it != registry.actionGroup || input.actionsEnabled }.toSet()
    val specs = registry.specsFor(visible)
    val missing = registry.visibleGroups(input.actionsEnabled).any { it !in visible }
    return if (missing) specs + registry.moreTools else specs
  }

  private fun historyBudget(provider: ProviderId): Int = if (provider == ProviderId.GROQ) config.historyBudgetGroq else config.historyBudgetOther

  private fun reasoningFor(level: ThinkingLevel, final: Boolean): ReasoningLevel = when (level) {
    ThinkingLevel.LOW -> if (final) ReasoningLevel.NONE else ReasoningLevel.LOW
    ThinkingLevel.MEDIUM -> if (final) ReasoningLevel.NONE else ReasoningLevel.MEDIUM
    ThinkingLevel.HIGH -> if (final) ReasoningLevel.LOW else ReasoningLevel.HIGH
  }

  private fun statusKeyFor(calls: List<ToolCall>): String {
    val first = calls.firstOrNull() ?: return "thinking"
    if (first.name == ToolRegistry.MORE_TOOLS) return "more_tools"
    return registry.find(first.name)?.group?.statusKey ?: "thinking"
  }

  /** Le parti grezze appartengono al modello che le ha scritte: cambiando modello si buttano. */
  private fun neutralize(messages: MutableList<Message>) {
    for (index in messages.indices) {
      val message = messages[index]
      if (message is Message.Assistant && message.raw != null) messages[index] = message.copy(raw = null, rawProvider = null)
    }
  }

  private suspend fun classifyWithFailover(
    input: AskInput<C>,
    attempt: Attempt,
    budget: TimeBudget,
    state: MutableStateFlow<AssistantState>,
    conversation: Conversation,
  ): RouterVerdict {
    while (true) {
      val ready = attempt.provider
      try {
        return router.classify(
          provider = ready.provider,
          model = ready.model(ModelTier.ROUTER),
          question = input.question,
          previousQuestion = conversation.exchanges.lastOrNull()?.question,
          previousGroups = conversation.lastGroups,
          language = input.language,
          actionsEnabled = input.actionsEnabled,
          hint = input.routerHint,
        )
      } catch (e: CancellationException) {
        throw e
      } catch (e: AiError.Unauthorized) {
        throw AssistantFailure(FailureKind.UNAUTHORIZED, e)
      } catch (e: Throwable) {
        // Lo stadio 1 non fa fallire la domanda: si prova il prossimo provider, poi il ripiego.
        val decision = failover.decide(e, ready.provider.id, remaining(input, attempt), attempt.waits, attempt.retries, budget.remainingMillis)
        when (decision) {
          is FailoverDecision.Switch -> {
            switchTo(input, attempt, decision.to, state)
            if (attempt.provider.provider.id == ProviderId.OPENROUTER) return RouterVerdict(allGroups(input))
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

  private fun switchTo(input: AskInput<C>, attempt: Attempt, to: ProviderId, state: MutableStateFlow<AssistantState>) {
    val next = input.providers.first { it.provider.id == to }
    state.value = AssistantState.SwitchingProvider(input.question, attempt.provider.provider.id, to)
    attempt.switched += to
    attempt.provider = next
    attempt.retries = 0
  }

  private suspend fun runTurnWithFailover(
    input: AskInput<C>,
    attempt: Attempt,
    budget: TimeBudget,
    state: MutableStateFlow<AssistantState>,
    request: ChatRequest,
    messages: List<Message>,
    tier: ModelTier,
    onWaited: (Int) -> Unit,
  ): TurnOutcome {
    var current = request
    while (true) {
      try {
        return runTurn(attempt.provider, current, input, state, tier)
      } catch (e: CancellationException) {
        throw e
      } catch (e: AssistantFailure) {
        throw e
      } catch (e: Throwable) {
        val decision = failover.decide(e, attempt.provider.provider.id, remaining(input, attempt), attempt.waits, attempt.retries, budget.remainingMillis)
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
          is FailoverDecision.Switch -> {
            switchTo(input, attempt, decision.to, state)
            // La stessa conversazione, riscritta per il nuovo provider: le parti grezze dell'altro non servono piu'.
            val neutral = messages.map { if (it is Message.Assistant) it.copy(raw = null, rawProvider = null) else it }
            val onOpenRouter = attempt.provider.provider.id == ProviderId.OPENROUTER
            current = current.copy(
              model = attempt.provider.model(tier),
              messages = if (current.messages.size > messages.size) neutral + current.messages.drop(messages.size) else neutral,
              tools = if (onOpenRouter && current.tools.isNotEmpty()) specsFor(allGroups(input), input) else current.tools,
            )
          }
          FailoverDecision.RetrySame -> {
            attempt.retries++
            delay(1_500)
          }
          is FailoverDecision.Fail -> throw AssistantFailure(decision.kind, e as? AiError, decision.retryAfterSec)
        }
      }
    }
  }

  /**
   * Un giro in stream: testo (mostrato dopo 24 caratteri o 150 ms senza tool call), tool call, fine.
   * Se il flusso si spezza a meta' — la rete cade, il server chiude, un pezzo non si legge — dopo
   * aver gia' portato qualcosa, si rifa' lo stesso giro senza stream, una volta: costa una richiesta
   * e risparmia i minuti gia' spesi nei tool. Un rifiuto (chiave, 400) non si riprova.
   */
  private suspend fun runTurn(ready: ReadyProvider, request: ChatRequest, input: AskInput<C>, state: MutableStateFlow<AssistantState>, tier: ModelTier): TurnOutcome {
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
      state.value = AssistantState.Working(input.question, 0, config.maxRounds, "thinking", 0, ready.provider.id, tier)
      val turn = ready.provider.complete(request)
      val message = turn.message
      val fullText = message.text?.takeIf { it.isNotBlank() }
      if (message.toolCalls.isEmpty() && fullText != null) {
        state.value = AssistantState.Answering(input.question, fullText, ready.provider.id, tier)
      }
      return TurnOutcome(fullText, message.toolCalls, message.raw, turn.usage, turn.rateLimit, turn.finishReason)
    }
    val calls = assembler.build()
    if (calls.isEmpty() && text.isNotEmpty() && !published) {
      state.value = AssistantState.Answering(input.question, text.toString(), ready.provider.id, tier)
    }
    return TurnOutcome(
      text = text.toString().takeIf { it.isNotBlank() },
      calls = calls,
      raw = raw,
      usage = finish?.usage,
      rateLimit = finish?.rateLimit ?: RateLimitInfo.EMPTY,
      finish = finish?.reason ?: if (calls.isNotEmpty()) FinishReason.TOOL_CALLS else FinishReason.STOP,
    )
  }

  private suspend fun executeParallel(
    calls: List<ToolCall>,
    ctx: C,
    budget: TimeBudget,
    traces: MutableList<ToolTrace>,
  ): List<ToolRun> = coroutineScope {
    val semaphore = Semaphore(config.parallelTools)
    val outcomes = calls.map { call ->
      async(Dispatchers.IO) {
        semaphore.withPermit {
          val started = clock()
          val output = if (call.name == ToolRegistry.MORE_TOOLS) {
            ToolOutput("ok: gli strumenti del gruppo ${call.arguments["gruppo"].string()} saranno disponibili dal prossimo passo")
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
          val limited = output.copy(text = ToolText.limit(output.text))
          ToolRun(call, limited) to ToolTrace(call.name, clock() - started, !limited.text.startsWith("errore"), limited.text.length)
        }
      }
    }.awaitAll()
    outcomes.forEach { traces += it.second }
    outcomes.map { it.first }
  }
}

/** La fine di una domanda che non ha risposta: la sessione la traduce in [AssistantState.Failed]. */
class AssistantFailure(val kind: FailureKind, val error: AiError?, val retryAfterSec: Int? = null) : Exception(error?.message ?: kind.name)

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
