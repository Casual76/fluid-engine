<!-- COPIA GENERATA da skill/fluid-engine/scripts/sync_docs.py a partire da skill/fluid-engine/references/api/ai-orchestrator.md.
     Non modificare qui: si cambia la fonte e si rilancia lo script. -->

# API · engine-ai: orchestratore, router, tool

_Generato sull'engine 3.0.4 da `scripts/gen_api_catalog.py`. Non si modifica a mano._

## `engine-ai/ai/orchestrator/AiConfirmationGate.kt`

- `enum class ConfirmationOutcome`
- `class AiConfirmationGate(val timeoutMillis: Long = DEFAULT_TIMEOUT_MILLIS)` — Il cancello delle conferme: un tool che vuole scrivere chiama [ask] e resta sospeso finche' la UI, che osserva [current], non chiama [resolve] — o finche' non scade il tempo.
  - `val current: StateFlow<PendingConfirmation?>` — La conferma a schermo, o null. E' sempre una: le altre aspettano in fila.
  - `val queued: StateFlow<Int>` — Quante richieste aspettano il loro turno dietro a quella a schermo: "1 di 3" per una card che lo dice.
  - `fun bindState(target: MutableStateFlow<AssistantState>, fallbackProvider: ProviderId = ProviderId.defaultOrder.first(), restore: (previous: AssistantState, awaiting: AssistantState.AwaitingConfirmation, current: AssistantState) -> AssistantState? = RestorePrevious)` — Il cancello scrive su [target] lo stato [AssistantState.AwaitingConfirmation] mentre una conferma e' a schermo: la domanda e il provider sono quelli dello stato di prima (o [fallbackProvider], se quello non ne ha uno: ascolto, trascrizione), le parole sono quelle della richiesta.
  - `fun unbindState()` — Il cancello smette di scrivere lo stato. Le conferme restano su [current].
  - `suspend fun ask(title: String, detail: String?): ConfirmationOutcome`
  - `fun resolve(id: Long, confirmed: Boolean)` — La risposta alla conferma a schermo: un [id] che non e' il suo (la richiesta di prima, una in fila) non conta.
  - `fun cancel()` — La domanda e' stata fermata: chi aspettava riceve un no — la conferma a schermo e tutte quelle in fila. Una richiesta che arriva dopo comincia da capo.
  - `companion object`
    - `const val DEFAULT_TIMEOUT_MILLIS`
    - `val RestorePrevious: (AssistantState, AssistantState.AwaitingConfirmation, AssistantState) -> AssistantState?` — La regola di serie di [bindState]: se lo stato e' ancora quello della conferma torna quello di prima, se e' arrivato altro nel frattempo non si tocca (uno stato vecchio non ne calpesta uno nuovo).

## `engine-ai/ai/orchestrator/AiDiagnosticsLog.kt`

- `data class ToolTrace(val name: String, val millis: Long, val ok: Boolean, val chars: Int)` — Una chiamata a un tool, com'e' andata.
- `data class ModelUse(val provider: ProviderId, val tier: ModelTier, val model: String)` — Un modello che ha risposto davvero in una domanda (1.29.0): quale provider, a quale livello, quale modello.
- `enum class SwitchReason` — Cosa e' successo a un servizio, in termini che si dicono all'utente (2.8.0): perche' si e' passati a un altro ([ProviderSwitch]), o perche' la domanda e' finita li' (`AssistantFailure.reason`).
  - `companion object`
    - `fun of(error: Throwable?): SwitchReason?` — Il perche' di un errore; null per quelli che non riguardano il servizio (chiave, contenuto bloccato, altro).
- `data class ProviderSwitch(val from: ProviderId, val to: ProviderId, val reason: SwitchReason)` — Un cambio di servizio a meta' domanda (2.8.0): da chi, a chi, e perche'.
- `data class AiRequestLog(val startedAtMillis: Long, val question: String, val mode: AskMode, val provider: ProviderId, val model: String, val switchedTo: List<ProviderId>, val groups: List<String>, val classifierUsed: Boolean, val tools: List<ToolTrace>, val steps: Int, val usage: Usage?, val durationMillis: Long, val outcome: String, val error: String?, val rateLimit: RateLimitInfo?, val waitedSeconds: Int, val tierReached: ModelTier = ModelTier.CHAT, val models: Map<ModelTier, String> = emptyMap(), val modelsUsed: List<ModelUse> = emptyList(), val switches: List<ProviderSwitch> = emptyList())` — Una domanda intera, per la pagina Diagnostica: chi ha risposto, con cosa, quanto e' costata.
- `class AiDiagnosticsLog(private val capacity: Int = 10)` — Le ultime dieci domande, in memoria: nessun contenuto sopravvive alla chiusura dell'app.
  - `val entries: StateFlow<List<AiRequestLog>>`
  - `val rateLimits: StateFlow<Map<ProviderId, RateLimitInfo>>`
  - `fun add(log: AiRequestLog)`
  - `fun rateLimit(provider: ProviderId, info: RateLimitInfo)`
  - `fun clear()`

## `engine-ai/ai/orchestrator/AiOrchestrator.kt`

- `data class AskResult(val answer: String, val chips: List<AnswerChip>, val provider: ProviderId, val usage: Usage?, val toolsUsed: List<String>, val log: AiRequestLog, val tierReached: ModelTier)` — Cio' che una domanda produce, oltre allo stato: la risposta e la sua traccia.
- `class AskInput<C>(val question: String, val mode: AskMode, val language: String, val settings: AiSettings, val providers: List<ReadyProvider>, val toolContext: C, val systemPrompt: String, val conversation: Conversation, val actionsEnabled: Boolean, val forceFinalPrompt: String = AiPrompts.forceFinal(language), val preselectedGroups: Set<AiToolGroup>? = null, val routerHint: Set<AiToolGroup> = emptySet(), val deepRequested: Boolean = false, val chipFilter: (AnswerChip) -> Boolean = { true }, val attachmentFallback: suspend (ContentPart) -> String? = { null }, val attachments: List<ContentPart> = emptyList(), val pinProvider: Boolean = false, val onModelUnavailable: (ProviderId, String) -> Unit = { _, _ -> })` — Cio' che serve all'orchestratore per una domanda, preparato dall'app: il contesto dei tool ([C]), il prompt, la conversazione, e i ganci con cui l'app orienta il giro senza che l'engine conosca il suo dominio.
- `data class AiOrchestratorConfig(val maxRounds: Int = 8, val maxMoreTools: Int = 2, val maxOutputTokens: Int = 1_500, val parallelTools: Int = 4, val toolTimeoutMillis: Long = 30_000L, val totalBudgetMillis: Long = 240_000L, val finalReserveMillis: Long = 20_000L, val historyBudgetGroq: Int = 5_000, val historyBudgetOther: Int = 60_000, val escalationChars: Int = 12_000, val toolTextChars: Int = ToolText.MAX_CHARS, val attachmentTextChars: Int = 12_000, val publishMinChars: Int = 24, val publishMinMillis: Long = 150L, val temperature: Double = 0.3, val maxLoadedTools: Int = 80, val maxOpens: Int = 4, val streamChunkTimeoutMillis: Int = 45_000, val thinkingChunkTimeoutMillis: Int = 120_000, val confirmationTimeoutMillis: Long = AiConfirmationGate.DEFAULT_TIMEOUT_MILLIS, val deepTierEnabled: Boolean = true)` — I numeri dell'orchestratore, tutti in un posto: l'app li alza o li abbassa per il suo caso.
  - `fun readTimeoutFor(tier: ModelTier, reasoning: ReasoningLevel, budgetRemainingMillis: Long): Int` — Il timeout di lettura di un giro (2.8.0): [thinkingChunkTimeoutMillis] se il modello pensa ([reasoning] diverso da NONE) o il livello e' il profondo, [streamChunkTimeoutMillis] altrimenti.
- `class AiOrchestrator<C>(private val registry: ToolRegistry<C>, private val router: AiRouter, private val diagnostics: AiDiagnosticsLog, private val failover: FailoverPolicy = FailoverPolicy(), private val config: AiOrchestratorConfig = AiOrchestratorConfig(), private val clock: () -> Long = System::currentTimeMillis, private val usageSink: AiUsageSink? = null, private val gate: AiConfirmationGate? = null)` — Il cervello: stadio 1 (gruppi, o il pre-router dell'app) -> giro dei tool in stream con esecuzione parallela, budget di tempo e di giri, cambio di provider sui 429 e sui 5xx, attesa col conto alla rovescia quando non c'e' nessuno a cui passare.
  - `val maxRounds: Int get()`
  - `suspend fun ask(input: AskInput<C>, state: MutableStateFlow<AssistantState>): AskResult`
  - `val confirmationWindowMillis: Long get()` — La finestra con cui l'utente puo' rispondere a una conferma: quella del cancello, se l'app l'ha dato.
  - `fun toolTimeoutMillis(tool: AiTool<C>, budgetRemainingMillis: Long, confirmationsInRound: Int = 1): Long` — Quanto aspetta l'orchestratore un tool prima di rispondere al modello "non ha risposto in tempo" (3.0.0). Dipende dal tool, da quanto budget resta nella domanda e da quante conferme stanno nel giro:
- `class AssistantFailure(val kind: FailureKind, val error: AiError?, val retryAfterSec: Int? = null, val provider: ProviderId? = null, val reason: SwitchReason? = null, val switches: List<ProviderSwitch> = emptyList()) : Exception(error?.message ?: kind.name)` — La fine di una domanda che non ha risposta: la sessione la traduce in [AssistantState.Failed]. Dalla 2.8.0 dice anche chi ha fallito per ultimo ([provider]), cosa gli e' successo ([reason]: un `tool_use_failed`, un modello sparito, una risposta vuota) e i cambi di servizio fatti prima ([switches]), per la frase della card e per il foglio "Dettagli".
  - `companion object`
    - `fun of(error: Throwable, other: FailureKind = FailureKind.UNKNOWN, provider: ProviderId? = null): AssistantFailure` — Il fallimento di un errore qualunque (3.0.0), per chi non passa dall'orchestratore: la trascrizione di una domanda parlata. [FailureKinds.of] sceglie la ragione ([other] per quelle che non sono di chiave, di limite o di rete), l'attesa del 429 diventa secondi interi (per eccesso: aspettare di meno non serve), e un [AssistantFailure] che c'e' gia' resta com'e'.
- `object ChipParser` — I marcatori `[[id]]` e `[[id:valore]]` in fondo alla risposta diventano chip; il testo mostrato non li ha. Quali id valgono lo decide l'app con [accept]; i chip ammessi sono al massimo tre.
  - `fun extract(text: String, accept: (AnswerChip) -> Boolean = { true }, max: Int = 3): Pair<String, List<AnswerChip>>`

## `engine-ai/ai/orchestrator/AiRouter.kt`

- `data class RouterVerdict(val groups: Set<AiToolGroup>, val deep: Boolean = false, val category: AiToolCategory? = null, val none: Boolean = false)` — Cosa lo stadio 1 ha deciso: i gruppi da caricare e se servira' il livello profondo. Con un catalogo gerarchico anche la [category] scelta (i gruppi vuoti = "apri la categoria con i suoi gruppi di partenza") e [none], che dice "questa domanda non ha bisogno di strumenti".
- `class AiRouter(private val groups: List<AiToolGroup>, private val actionGroup: AiToolGroup?, private val domainHint: String, private val defaultGroups: List<AiToolGroup>, private val maxGroups: Int = MAX_GROUPS, private val categories: List<AiToolCategory> = emptyList())` — Lo stadio 1 del catalogo a due stadi: una chiamata piccola, con uscita strutturata, che sceglie i gruppi di strumenti (al massimo [maxGroups]) e dice se la domanda avra' bisogno di leggere un documento o molto testo — nel qual caso l'orchestratore parte gia' dal livello profondo. Non fa mai fallire la domanda: se il modello sbaglia o non risponde, c'e' [fallback].
  - `val hierarchical: Boolean get()`
  - `val schema: JsonObject`
  - `fun prompt(language: String, actionsEnabled: Boolean, loadedCategories: Set<AiToolCategory> = emptySet()): String`
  - `suspend fun classify(provider: ChatProvider, model: String, question: String, previousQuestion: String?, previousGroups: Set<AiToolGroup>, language: String, actionsEnabled: Boolean, hint: Set<AiToolGroup> = emptySet(), loadedCategories: Set<AiToolCategory> = emptySet()): RouterVerdict`
  - `suspend fun classifyWithTurn(provider: ChatProvider, model: String, question: String, previousQuestion: String?, previousGroups: Set<AiToolGroup>, language: String, actionsEnabled: Boolean, hint: Set<AiToolGroup> = emptySet(), loadedCategories: Set<AiToolCategory> = emptySet()): Pair<RouterVerdict, ChatTurn>` — Come [classify], ma torna anche il turno del provider: l'orchestratore ne registra l'uso.
  - `fun parse(text: String?, actionsEnabled: Boolean, loadedCategories: Set<AiToolCategory> = emptySet()): RouterVerdict?`
  - `fun fallback(previousGroups: Set<AiToolGroup>): Set<AiToolGroup>`
  - `companion object`
    - `const val MAX_GROUPS`
    - `const val NONE` — La categoria che il router scrive quando la domanda non ha bisogno di strumenti.
- `object AiPrompts` — Le frasi senza dominio che l'orchestratore aggiunge da solo, nella lingua dell'app.
  - `fun forceFinal(language: String): String` — Il system prompt aggiunto all'ultimo giro: niente altri strumenti, si risponde con quello che c'e'.
  - `fun attachmentIntro(language: String, name: String?): String` — La riga che precede un allegato passato al modello come parte di un messaggio.
  - `fun attachmentUnreadable(language: String, name: String?): String` — Quando il modello non regge l'allegato e l'app non sa tradurlo in testo.
  - `fun attachmentDropped(language: String, name: String?): String` — Nella storia riletta per un altro modello: l'allegato c'era, questo modello non lo legge.
  - `fun userAttachmentText(language: String, name: String?): String` — La riga che precede il testo di un allegato dell'utente tradotto dall'app.

## `engine-ai/ai/orchestrator/AiUsageSink.kt`

- `data class AiUsageEvent(val atMillis: Long, val conversationId: Long, val provider: ProviderId, val model: String, val tier: ModelTier, val usage: Usage?, val rateLimit: RateLimitInfo, val durationMillis: Long, val error: AiError? = null)` — Una chiamata a un provider, com'e' andata: chi, con quale modello e livello, quanti token, cosa dicevano gli header sui limiti, quanto ha aspettato, e l'errore se non ha risposto. Una per turno — il router, ogni giro della chat, la riprova senza stream — non una per domanda: un'app che tiene il conto dei consumi per modello li vuole separati.
  - `val rateLimited: Boolean get()`
- `fun interface AiUsageSink` — Dove l'orchestratore e il router versano gli [AiUsageEvent] (1.26.0). L'engine non persiste niente: [AiDiagnosticsLog] tiene le ultime dieci domande in memoria, e per un tracker dei consumi che sopravviva alla chiusura dell'app serve un deposito dell'app. Viene chiamato sul thread della domanda: chi scrive su disco fa partire il lavoro altrove.
  - `fun onTurn(event: AiUsageEvent)`

## `engine-ai/ai/orchestrator/AssistantState.kt`

- `data class MicLevel(val level: Float = 0f, val speaking: Boolean = false)` — Quanto forte si sta sentendo, e se in questo istante e' parlato: la materia dell'aureola.
- `enum class AskMode` — Come e' arrivata la domanda: decide se la risposta va anche letta ad alta voce.
- `data class AnswerChip(val id: String, val value: String? = null)` — Un chip sotto la risposta: il modello scrive `[[id]]` o `[[id:valore]]`, la card lo rende toccabile. L'engine non sa cosa siano gli id: l'app li dichiara al modello nel prompt e li filtra con `AskInput.chipFilter`, poi li traduce in una pagina, un posto, un dettaglio.
- `enum class FailureKind` — Perche' e' andata male, in termini che la UI sa tradurre in una frase. Dalla 2.8.0 anche [MODEL_UNAVAILABLE] (il modello scelto non c'e' piu' e non c'era un altro servizio a cui passare) e [CONTEXT_TOO_LONG] (la domanda non ci stava nemmeno con la storia accorciata): in coda, perche' un'app che avesse salvato un ordinale non ne veda cambiare nessuno.
- `data class PendingConfirmation(val id: Long, val title: String, val detail: String?)` — Un'azione che aspetta il si' dell'utente: solo le parole con cui mostrarla. L'oggetto vero dell'azione lo tiene l'app, che e' l'unica a sapere cosa sia.
- `sealed interface AssistantState` — Lo stato dell'assistente, uno solo per volta: la UI lo osserva e disegna aureola, card e stati leggibili. Le stringhe non stanno qui (le chiavi si', le parole le mette la UI).
  - `data object Idle : AssistantState`
  - `data class Listening(val elapsedMillis: Long) : AssistantState` — In ascolto. Il livello del microfono **non** sta qui: cambiava cinquanta volte al secondo e ricomponeva tutto l'overlay a ogni frame audio (facendo ripartire, fra l'altro, gli effetti che fermavano la voce di sistema). Sta in un flusso a parte, che legge solo l'aureola.
  - `data object Transcribing : AssistantState`
  - `data object HeardNothing : AssistantState` — Nessun parlato: la card lo dice e si chiude da sola.
  - `data class Classifying(val question: String, val provider: ProviderId) : AssistantState`
  - `data class Working(val question: String, val step: Int, val maxSteps: Int, val statusKey: String, val statusExtra: Int, val provider: ProviderId, val tier: ModelTier = ModelTier.CHAT) : AssistantState` — Il giro dei tool. [statusKey] e' la chiave del testo di stato: lo `statusKey` di un gruppo, "thinking", "more_tools"; [statusExtra] quante chiamate in parallelo; [tier] con quale livello di modello si sta lavorando (la UI puo' dire "puo' volerci un po'" sul profondo).
  - `data class WaitingRateLimit(val question: String, val provider: ProviderId, val secondsLeft: Int) : AssistantState`
  - `data class SwitchingProvider(val question: String, val from: ProviderId, val to: ProviderId, val reason: SwitchReason? = null) : AssistantState` — Si passa a un altro servizio; [reason] dice perche' (2.8.0), cosi' la card puo' dirlo ("Groq e' al limite").
  - `data class Answering(val question: String, val partial: String, val provider: ProviderId, val tier: ModelTier = ModelTier.CHAT) : AssistantState`
  - `data class AwaitingConfirmation(val question: String, val confirmation: PendingConfirmation, val provider: ProviderId) : AssistantState` — Un'azione con conferma: la card mostra i chip Conferma/Annulla finche' l'utente non sceglie.
  - `data class Done(val question: String, val answer: String, val chips: List<AnswerChip>, val provider: ProviderId, val mode: AskMode, val usage: Usage?, val toolsUsed: List<String>, val durationMillis: Long, val tierReached: ModelTier = ModelTier.CHAT, val switches: List<ProviderSwitch> = emptyList()) : AssistantState`
  - `data class Failed(val question: String?, val kind: FailureKind, val error: AiError?, val retryAfterSec: Int?, val partial: String?, val provider: ProviderId? = null, val reason: SwitchReason? = null) : AssistantState` — La domanda e' finita senza risposta. Dalla 2.8.0 [provider] e' il servizio che ha fallito per ultimo e [reason] cosa gli e' successo (da [AssistantFailure]): con [AiError.httpCode] e [AiError.providerMessage] sono la materia di un foglio "Dettagli".
  - `data class Cancelled(val question: String?, val partial: String?) : AssistantState`
  - `val isBusy: Boolean`
- `fun AssistantState.questionOrNull(): String?` — La domanda a cui lo stato si riferisce, se ne ha una (3.0.0): ogni app se ne scriveva una copia per riscrivere lo stato con una conferma o con un errore senza perdere il testo della domanda.
- `fun AssistantState.providerOrNull(): ProviderId?` — Il provider che lo stato dice di stare usando (3.0.0): quello di arrivo per un cambio di servizio, l'ultimo che ha fallito per un [AssistantState.Failed]; null dove non c'e' un provider (riposo, ascolto, trascrizione).

## `engine-ai/ai/orchestrator/Conversation.kt`

- `data class Exchange(val question: String, val answer: String, val chips: List<AnswerChip>, val provider: ProviderId, val atMillis: Long, val attachments: List<ContentPart> = emptyList())` — Uno scambio finito, per la card: la domanda, la risposta, i chip. [attachments] sono le parti (immagini, documenti) che l'utente aveva messo nella domanda: il compattatore le ripropone solo per l'ultimo scambio, cosi' "e a destra cosa c'e'?" trova ancora lo screenshot.
- `class Conversation(val id: Long, val startedAtMillis: Long)` — La conversazione in memoria: gli scambi conclusi, il traffico tool dell'ultima domanda (per i "e domani?"), i gruppi gia' aperti, il provider su cui si e' finiti. L'engine la tiene solo in memoria; un'app che vuole conservarla la ricostruisce da cio' che ha salvato ([exchanges] basta, il traffico tool si perde e va bene cosi';
  - `val exchanges`
  - `var lastToolRound: List<Message>` — `Assistant(toolCalls)` + `ToolResult` dell'ultima domanda, da riproporre una volta sola.
  - `var lastGroups: Set<AiToolGroup>` — I gruppi dell'ultima domanda: il ripiego del router quando non capisce quella dopo.
  - `val loadedGroups: LinkedHashSet<AiToolGroup>` — I gruppi aperti in questa conversazione (1.26.0), dal meno al piu' recente uso. Restano aperti finche' la conversazione vive: quello che il modello ha chiesto una volta non lo richiede a ogni domanda. L'orchestratore ne toglie dalla testa solo quando i loro tool superano il tetto (`AiOrchestratorConfig.maxLoadedTools`).
  - `val loadedCategories: LinkedHashSet<AiToolCategory>` — Le categorie aperte: quelle dei gruppi aperti, piu' quelle aperte senza sottocategoria.
  - `var lastActivityMillis: Long`
  - `var provider: ProviderId?`
  - `fun touch(groups: Collection<AiToolGroup>)` — Segna questi gruppi come usati adesso: vanno in coda, e sono gli ultimi a cadere.
  - `fun isExpired(nowMillis: Long, ttlMillis: Long = TTL_MILLIS): Boolean`
  - `companion object`
    - `const val TTL_MILLIS: Long`
- `object HistoryCompactor` — Cosa del passato si manda al modello: le coppie domanda/risposta (solo testo, le vecchie accorciate), il traffico tool dell'ultima domanda al suo posto, gli allegati dell'ultima domanda, e una guardia sui token (prima cadono gli allegati, poi il traffico tool, poi le coppie piu' vecchie, mai l'ultima). Un `Assistant` con tool call cade sempre insieme ai suoi risultati: i provider rispondono 400 a un risultato orfano.
  - `const val MAX_PAIRS`
  - `const val OLD_ANSWER_CHARS`
  - `const val CHARS_PER_TOKEN`
  - `fun compact(conversation: Conversation, budgetTokens: Int, includeToolRound: Boolean = true, includeAttachments: Boolean = true): List<Message>`
  - `fun estimateTokens(messages: List<Message>): Int`
  - `fun estimateTokens(text: String): Int`

## `engine-ai/ai/orchestrator/FailoverPolicy.kt`

- `class TimeBudget(private val startMillis: Long, private val totalMillis: Long = TOTAL_MILLIS, private val finalReserveMillis: Long = FINAL_RESERVE_MILLIS, private val clock: () -> Long)` — Quanto tempo resta a una domanda: [totalMillis] in tutto, poi si risponde con quello che si sa. I default sono quelli di una card che aspetta a schermo; un'app che lavora in un service ne passa di piu' larghi.
  - `val remainingMillis: Long get()`
  - `val elapsedMillis: Long get()`
  - `val forceFinal: Boolean get()` — Vero quando conviene chiudere: l'ultimo giro deve avere il tempo di scrivere.
  - `companion object`
    - `const val TOTAL_MILLIS: Long`
    - `const val FINAL_RESERVE_MILLIS: Long`
- `sealed interface FailoverDecision`
  - `data class Wait(val seconds: Int) : FailoverDecision`
  - `data class Switch(val to: ProviderId) : FailoverDecision`
  - `data object RetrySame : FailoverDecision`
  - `data class Fail(val kind: FailureKind, val retryAfterSec: Int?) : FailoverDecision`
  - `data object TrimAndRetry : FailoverDecision` — La richiesta non ci sta (2.8.0): stesso provider, con la storia compattata a meta' budget e i risultati dei tool piu' corti. Una volta sola; poi si cambia provider o si fallisce.
- `class FailoverPolicy(private val maxWaits: Int = 2, private val maxWaitSec: Int = 60)` — Cosa fare quando un provider fallisce, in una tabella: 429 -> il prossimo della lista, e se non c'e' nessuno si aspetta il `retry-after` (al massimo due volte, mai piu' del budget); 5xx e rete -> un secondo tentativo, poi il prossimo; chiave sbagliata -> ci si ferma e lo si dice, perche' un ripiego silenzioso nasconderebbe l'errore di configurazione.
  - `fun decide(error: Throwable, current: ProviderId, remaining: List<ProviderId>, waitsDone: Int, retriesDone: Int, budgetRemainingMillis: Long, pinned: Boolean = false, trimsDone: Int = 0): FailoverDecision`
  - `fun decideEmpty(finish: FinishReason, current: ProviderId, remaining: List<ProviderId>, emptyRetriesDone: Int, budgetRemainingMillis: Long, pinned: Boolean = false): FailoverDecision` — Una risposta finale vuota (2.8.0): un giro che chiude senza tool call e senza una parola. Prima finiva salvata come risposta riuscita — una bolla vuota — tranne sul giro forzato. Adesso non e' mai un successo: una riprova sullo stesso provider (quando la fine era `LENGTH` l'orchestratore la fa con piu' token o meno ragionamento;
  - `companion object`
    - `const val DEFAULT_WAIT_SEC`
    - `const val RETRY_MIN_BUDGET_MILLIS` — Sotto questo tempo rimasto una riprova non ha spazio per finire: si passa oltre.

## `engine-ai/ai/orchestrator/FailureKinds.kt`

- `object FailureKinds` — Da un errore del provider alla ragione che si dice all'utente (3.0.0), per cio' che non passa dal [FailoverPolicy]: la trascrizione di una domanda parlata. La domanda scritta ha la sua tabella ([FailoverPolicy.decide]);
  - `fun of(error: Throwable, other: FailureKind = FailureKind.UNKNOWN): FailureKind`

## `engine-ai/ai/tools/Tools.kt`

- `interface AiToolCategory` — Una categoria del catalogo a piu' livelli (1.26.0): un'app, un'area di cose che l'assistente sa fare. Lo stadio 1 sceglie la categoria (o nessuna) e le sue sottocategorie; il modello della chat ne apre altre da se' con i tool built-in [ToolRegistry.OPEN_CATEGORY] e [ToolRegistry.OPEN_GROUP]. Un'app con un dominio solo non ne dichiara e il catalogo resta piatto, come prima.
  - `val id: String`
  - `val label: String`
  - `val hint: String`
- `interface AiToolGroup` — Un gruppo del catalogo a due stadi: lo stadio 1 sceglie i gruppi, lo stadio 2 riceve i tool di quei gruppi. L'app li definisce (di solito con un enum), l'engine non sa cosa contengano.
  - `val id: String`
  - `val statusKey: String`
  - `val hint: String`
  - `val category: AiToolCategory? get()`
  - `val parent: AiToolGroup? get()`
  - `val loadsWithCategory: Boolean get()`
  - `val toolTimeoutMillis: Long? get()` — Quanto puo' lavorare un tool di questo gruppo, **senza** contare l'attesa di una conferma (3.0.0); null = il tetto dell'orchestratore ([AiOrchestratorConfig.toolTimeoutMillis]). Chi legge da una rete dichiara qui i suoi venti secondi; chi scrive dichiara quanto ci mette a scrivere, e se aspetta l'utente lo dice con [awaitsConfirmation] senza sommare niente a mano.
  - `val awaitsConfirmation: Boolean get()` — Vero se un tool di questo gruppo puo' fermarsi dentro `run` ad aspettare il si' dell'utente (un [AiConfirmationGate]; 3.0.0). All'attesa del tool l'orchestratore aggiunge la finestra del cancello, una per ogni conferma che puo' esserci in coda nello stesso giro.
- `val AiToolGroup.resolvedCategory: AiToolCategory?` — La categoria di un gruppo, risalendo i padri: null se il catalogo e' piatto.
- `data class ToolOutput(val text: String, val parts: List<ContentPart> = emptyList(), val escalate: Boolean = false)` — Cio' che un tool restituisce: testo compatto per il modello e, se l'ha trovato, un allegato (immagini, un documento) che l'orchestratore passa al modello come parte di un messaggio se il modello lo regge, o fa tradurre in testo dall'app se non lo regge.
  - `companion object`
    - `fun error(message: String): ToolOutput`
- `data class ConfirmationText(val title: String, val detail: String? = null)` — Le parole con cui chiedere conferma di un'azione: il titolo del tasto e il dettaglio sotto.
- `interface AiTool<C>` — Un tool: un nome, un gruppo, una descrizione e uno schema per il modello, e un [run] che riceve gli argomenti e il contesto dell'app ([C], quello che i tool possono toccare: dati, ora, azioni).
  - `val name: String`
  - `val group: AiToolGroup`
  - `val description: String`
  - `val parameters: JsonObject`
  - `suspend fun run(args: JsonObject, ctx: C): ToolOutput` — Il risultato e' testo compatto per il modello: righe `chiave: valore`, mai JSON verboso.
  - `val spec: ToolSpec get()`
  - `val needsConfirmation: Boolean get()` — Vero se scrive o costa: chi lo chiama da fuori chiede conferma all'utente prima di [run].
  - `val longRunning: Boolean get()` — Vero se puo' durare piu' di una chiamata (un'installazione, una conversione di file).
  - `val isAction: Boolean get()` — Vero se agisce nell'app invece di leggere: sparisce quando l'utente spegne le azioni.
  - `suspend fun describe(args: JsonObject, ctx: C): ConfirmationText?` — Le parole per la conferma con questi argomenti; null = chi chiama ne inventa di generiche.
  - `val timeoutMillis: Long? get()` — Quanto puo' lavorare questo tool, **senza** l'attesa di una conferma (3.0.0). Di serie e' quello del gruppo ([AiToolGroup.toolTimeoutMillis]); null = il tetto dell'orchestratore.
  - `val awaitsConfirmation: Boolean get()` — Vero se [run] puo' fermarsi ad aspettare il si' dell'utente (3.0.0): di serie [needsConfirmation] oppure il gruppo lo dichiara. Un tool di un gruppo che ne ospita di due tipi (leggere e scrivere) dice di si' anche quando non aspetta: il tetto e' piu' largo, e un tool che finisce prima non lo usa. Un tool che chiede in un gruppo che non lo dichiara scrive `override val awaitsConfirmation = true`.
- `class ToolRegistry<C>(val tools: List<AiTool<C>>, val groups: List<AiToolGroup>, val actionGroup: AiToolGroup? = null)` — Il catalogo: tutti i tool, quelli di un insieme di gruppi, e i tool built-in con cui il modello chiede quello che lo stadio 1 non gli ha dato. [groups] e' l'elenco completo dei gruppi dell'app, nell'ordine in cui il router li legge; [actionGroup] e' quello che sparisce quando le azioni nell'app sono spente.
  - `val categories: List<AiToolCategory>` — Le categorie, nell'ordine in cui compaiono nei gruppi; vuoto se il catalogo e' piatto.
  - `val hierarchical: Boolean get()`
  - `fun specsFor(groups: Set<AiToolGroup>): List<ToolSpec>`
  - `fun allSpecs(): List<ToolSpec>`
  - `fun find(name: String): AiTool<C>?`
  - `fun group(id: String?): AiToolGroup?`
  - `fun category(id: String?): AiToolCategory?`
  - `fun groupsOf(category: AiToolCategory): List<AiToolGroup>` — I gruppi di una categoria, a qualunque livello.
  - `fun topGroupsOf(category: AiToolCategory): List<AiToolGroup>` — Le sottocategorie di primo livello di una categoria.
  - `fun childrenOf(group: AiToolGroup): List<AiToolGroup>`
  - `fun initialGroupsOf(category: AiToolCategory): List<AiToolGroup>` — Cosa si apre aprendo una categoria senza dire quale sottocategoria: i gruppi marcati [AiToolGroup.loadsWithCategory], o tutti quelli di primo livello se l'app non ne ha marcati.
  - `fun toolCount(groups: Set<AiToolGroup>): Int` — Quanti tool stanno in un insieme di gruppi: per il tetto dell'orchestratore.
  - `fun visibleGroups(actionsEnabled: Boolean): List<AiToolGroup>` — I gruppi che il modello puo' vedere: tutti, meno quello delle azioni se sono spente.
  - `val moreTools: ToolSpec`
  - `val openCategoryTool: ToolSpec` — Il tool con cui il modello apre una categoria (un'app, un'area) che non ha ancora: al giro dopo arrivano i suoi strumenti di partenza e la lista delle sue sottocategorie. La descrizione elenca le categorie con la loro riga: e' quello che il modello legge per capire dove sta cio' che gli serve, e costa una riga per categoria a ogni giro.
  - `fun openGroupTool(loadedCategories: Set<AiToolCategory>, loaded: Set<AiToolGroup>, actionsEnabled: Boolean): ToolSpec?` — Il tool con cui il modello apre una sottocategoria delle categorie gia' aperte: solo quelle non ancora aperte, con la loro riga. Null se non c'e' niente da aprire.
  - `val deepTool: ToolSpec` — Il tool con cui il modello dice "questa e' difficile": l'orchestratore lo mostra solo finche' si sta lavorando col modello della chat e solo se il provider ne ha davvero uno piu' capace, e dal giro dopo il lavoro continua li', con la stessa storia.
  - `companion object`
    - `const val MORE_TOOLS`
    - `const val DEEP_MODEL`
    - `const val OPEN_CATEGORY`
    - `const val OPEN_GROUP`
    - `val BUILT_IN: Set<String>` — I nomi che l'orchestratore gestisce da se', senza cercarli fra i tool dell'app.
- `object Schema` — Gli schemi JSON dei parametri, brevi: ogni parola nello schema costa token a ogni giro.
  - `fun obj(properties: Map<String, JsonObject>, required: List<String> = emptyList()): JsonObject`
  - `fun str(description: String, enum: List<String>? = null): JsonObject`
  - `fun int(description: String, minimum: Int? = null, maximum: Int? = null): JsonObject`
  - `fun bool(description: String): JsonObject`
  - `fun strArray(description: String, enum: List<String>? = null): JsonObject`
- `object Args` — Lettura tollerante degli argomenti: il modello scrive numeri come stringhe e viceversa.
  - `fun JsonObject.str(key: String): String?`
  - `fun JsonObject.int(key: String): Int?`
  - `fun JsonObject.double(key: String): Double?`
  - `fun JsonObject.bool(key: String): Boolean?`
  - `fun JsonObject.list(key: String): List<String>`
- `object ToolText` — Il testo che torna al modello: righe brevi, un budget di caratteri (~600 token) oltre il quale si tronca dicendo quante righe mancano. I tool tagliano prima le loro liste; questo e' l'ultimo argine, e serve soprattutto su Groq.
  - `const val MAX_CHARS`
  - `fun limit(text: String, maxChars: Int = MAX_CHARS): String`
  - `fun omitted(missing: Int): String` — Cosa manca, al singolare e al plurale (3.0.0): prima "altre 1 righe omesse".
  - `class Builder(private val maxChars: Int = MAX_CHARS)`
    - `fun line(text: String)`
    - `fun line(key: String, value: Any?)`
    - `fun blank()`
    - `fun build(): String`
  - `inline fun build(maxChars: Int = MAX_CHARS, block: Builder.() -> Unit): String`
  - `inline fun output(maxChars: Int = MAX_CHARS, block: Builder.() -> Unit): ToolOutput` — La scorciatoia per il caso comune: un tool che risponde con solo testo. [maxChars] serve al tool che raccoglie davvero tanto (tutti i voti dell'anno, tutte le materie insieme) e che troncato a duemila caratteri farebbe rispondere il modello su meta' dei dati — ma deve restare sotto al tetto dell'orchestratore (`AiOrchestratorConfig.toolTextChars`), che ha l'ultima parola.
